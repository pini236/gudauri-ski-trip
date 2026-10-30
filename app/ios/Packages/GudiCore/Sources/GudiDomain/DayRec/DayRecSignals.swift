import Foundation

/// Steps 1 to 3 and 6, 9, 10 of the analysis (tools/dayrec/analyze.py, in its order): the Kalman filter, the
/// maximum speed, the altitude, the map matching, the falls and the run that stops by itself.
extension DayRec {
    typealias Pt = DayJournal.Point

    static let stand = 0, walk = 1, ski = 2, up = 3
    static let acts = ["stand", "walk", "ski", "up"]
    static let neg = -1e9

    /// Python's round(x, n), half to even on the scaled value.
    static func round(_ v: Double, _ n: Int) -> Double {
        let f = pow(10.0, Double(n))
        return (v * f).rounded(.toNearestOrEven) / f
    }

    static func pyRound(_ v: Double) -> Int { Int(v.rounded(.toNearestOrEven)) }

    static func kmh(_ v: Double) -> Double { round(v * 3.6, 2) }

    static func sec(_ a: Int64, _ b: Int64) -> Double { Double(b - a) / 1000 }

    // MARK: 1. Kalman filter, per axis, state (position, velocity)

    struct Axis {
        var p = 0.0, v = 0.0
        /// covariance [[a, b], [b, c]]
        var a = 0.0, b = 0.0, c = 0.0

        mutating func reset(_ z: Double, _ rz: Double, _ v0: Double, _ rv: Double) { p = z; v = v0; a = rz; b = 0; c = rv }

        mutating func predict(_ dt: Double, _ q: Double) {
            p += v * dt
            let q2 = q * q, a0 = a, b0 = b, c0 = c
            a = a0 + 2 * b0 * dt + c0 * dt * dt + q2 * pow(dt, 4) / 4
            b = b0 + c0 * dt + q2 * pow(dt, 3) / 2
            c = c0 + q2 * dt * dt
        }

        func innovPos(_ z: Double, _ r: Double) -> (Double, Double) { (z - p, a + r) }

        mutating func updPos(_ y: Double, _ s: Double) {
            let k0 = a / s, k1 = b / s, a0 = a, b0 = b, c0 = c
            p += k0 * y; v += k1 * y
            a = (1 - k0) * a0; b = (1 - k0) * b0; c = c0 - k1 * b0
        }

        func innovVel(_ z: Double, _ r: Double) -> (Double, Double) { (z - v, c + r) }

        mutating func updVel(_ y: Double, _ s: Double) {
            let k0 = b / s, k1 = c / s, a0 = a, b0 = b, c0 = c
            p += k0 * y; v += k1 * y
            a = a0 - k0 * b0; b = b0 - k0 * c0; c = (1 - k1) * c0
        }
    }

    /// The filtered (or smoothed) position and velocity of a reading, local metres.
    struct KF { var x: Double, y: Double, vx: Double, vy: Double }

    static func kalman(_ xy: [(x: Double, y: Double)], _ pts: [Pt], _ P: Params, smooth: Bool) -> [KF] {
        let q = P["kalman", "q_acc"], rmin = P["kalman", "pos_sigma_min_m"], vmin = P["kalman", "vel_sigma_min_mps"]
        let gate = P["kalman", "gate_chi2"], gn = P.int("kalman", "gate_reset_n"), still = P["kalman", "still_mps"]
        var X = Axis(), Y = Axis()
        let n = pts.count
        var fx: [Axis] = [], fy: [Axis] = [], px: [Axis] = [], py: [Axis] = []
        var dts: [Double] = [], starts: [Bool] = []
        fx.reserveCapacity(n); fy.reserveCapacity(n); px.reserveCapacity(n); py.reserveCapacity(n)
        var gated = 0
        for i in 0..<n {
            let pt = pts[i], x = xy[i].x, y = xy[i].y
            let r = pow(max(rmin, pt.hacc ?? 10), 2)
            var vel: (Double, Double, Double)?
            if let s = pt.spd, let b = pt.crs {
                let rv = pow(max(vmin, pt.sacc ?? 1), 2)
                let br = b * (Double.pi / 180)
                vel = (s * sin(br), -s * cos(br), rv)
            } else if let s = pt.spd, s < still {
                // no bearing because the phone is not moving: the velocity is about zero
                vel = (0, 0, pow(max(vmin, s + (pt.sacc ?? 1)), 2))
            }
            let dt = i > 0 ? sec(pts[i - 1].t, pt.t) : 0
            if i == 0 || dt > P.resetGapS || gated >= gn {
                let v0 = vel ?? (0, 0, 16)
                X.reset(x, r, v0.0, v0.2); Y.reset(y, r, v0.1, v0.2)
                gated = 0
                starts.append(true); px.append(X); py.append(Y); dts.append(0)
            } else {
                X.predict(dt, q); Y.predict(dt, q)
                starts.append(false); px.append(X); py.append(Y); dts.append(dt)
                let (yx, sx) = X.innovPos(x, r), (yy, sy) = Y.innovPos(y, r)
                if yx * yx / sx + yy * yy / sy > gate {
                    gated += 1
                } else {
                    gated = 0
                    X.updPos(yx, sx); Y.updPos(yy, sy)
                }
                if let vel {
                    let (ux, tx) = X.innovVel(vel.0, vel.2), (uy, ty) = Y.innovVel(vel.1, vel.2)
                    if ux * ux / tx + uy * uy / ty <= gate { X.updVel(ux, tx); Y.updVel(uy, ty) }
                }
            }
            fx.append(X); fy.append(Y)
        }
        if !smooth { return (0..<n).map { KF(x: fx[$0].p, y: fy[$0].p, vx: fx[$0].v, vy: fy[$0].v) } }
        let sx = rts(fx, px, dts, starts), sy = rts(fy, py, dts, starts)
        return (0..<n).map { KF(x: sx[$0].0, y: sy[$0].0, vx: sx[$0].1, vy: sy[$0].1) }
    }

    /// Rauch-Tung-Striebel smoother for one axis; never across a reset.
    static func rts(_ f: [Axis], _ p: [Axis], _ dts: [Double], _ starts: [Bool]) -> [(Double, Double)] {
        let n = f.count
        guard n > 0 else { return [] }
        var out = [(Double, Double)](repeating: (0, 0), count: n)
        out[n - 1] = (f[n - 1].p, f[n - 1].v)
        var k = n - 2
        while k >= 0 {
            defer { k -= 1 }
            if starts[k + 1] { out[k] = (f[k].p, f[k].v); continue }
            let dt = dts[k + 1]
            let a = f[k].a, b = f[k].b, c = f[k].c
            // C = Pf F^T Pp^-1, F = [[1, dt], [0, 1]]
            let m00 = a + b * dt, m01 = b, m10 = b + c * dt, m11 = c
            let pa = p[k + 1].a, pb = p[k + 1].b, pc = p[k + 1].c
            let det = pa * pc - pb * pb
            if det <= 0 { out[k] = (f[k].p, f[k].v); continue }
            let i00 = pc / det, i01 = -pb / det, i11 = pa / det
            let c00 = m00 * i00 + m01 * i01, c01 = m00 * i01 + m01 * i11
            let c10 = m10 * i00 + m11 * i01, c11 = m10 * i01 + m11 * i11
            let d0 = out[k + 1].0 - p[k + 1].p, d1 = out[k + 1].1 - p[k + 1].v
            out[k] = (f[k].p + c00 * d0 + c01 * d1, f[k].v + c10 * d0 + c11 * d1)
        }
        return out
    }

    // MARK: 2. Maximum speed, from the raw Doppler readings

    static func validSpeeds(_ pts: [Pt], _ xy: [(x: Double, y: Double)], _ P: Params) -> [Bool] {
        let n = pts.count
        let W = P.int("max_speed", "pos_window_s"), mdt = P["max_speed", "max_pair_dt_s"]
        let accMax = P["max_speed", "speed_acc_max_mps"], accel = P["max_speed", "accel_max_mps2"]
        let rel = P["max_speed", "pos_match_rel"], abs_ = P["max_speed", "pos_match_abs_mps"]
        var ok = [Bool](repeating: false, count: n)
        for i in 0..<n {
            guard let s = pts[i].spd, let sa = pts[i].sacc, sa <= accMax else { continue }
            var good = true
            for j in [i - 1, i + 1] where j >= 0 && j < n {   // acceleration against both neighbours
                if let sj = pts[j].spd {
                    let dt = abs(Double(pts[j].t - pts[i].t)) / 1000
                    if dt <= mdt && abs(sj - s) / dt > accel { good = false }
                }
            }
            if !good { continue }
            // the Doppler speed agrees with how far the raw positions moved, over i-W .. i+W
            let lo = i - W, hi = i + W
            if lo < 0 || hi >= n { continue }
            var path = 0.0, dur = 0.0, sd = 0.0
            for k in (lo + 1)...hi {
                let dt = sec(pts[k - 1].t, pts[k].t)
                guard dt <= mdt, let a = pts[k].spd, let b = pts[k - 1].spd else { good = false; break }
                path += hypot(xy[k].x - xy[k - 1].x, xy[k].y - xy[k - 1].y)
                dur += dt
                sd += (a + b) / 2 * dt
            }
            if !good { continue }
            if abs(path - sd) > max(rel * sd, abs_ * dur) { continue }
            ok[i] = true
        }
        return ok
    }

    /// The highest speed held over sustain_s readings in a row, all valid: (m/s, point index of the lowest of them).
    static func maxSpeed(_ pts: [Pt], _ ok: [Bool], _ idx0: [Int], _ P: Params) -> (v: Double, at: Int)? {
        let n = P.int("max_speed", "sustain_s"), mdt = P["max_speed", "max_pair_dt_s"]
        let idx = idx0.sorted()
        var best: (v: Double, at: Int)?
        guard idx.count - n + 1 > 0 else { return nil }
        for a in 0..<(idx.count - n + 1) {
            let w = idx[a..<(a + n)]
            var fine = true
            for k in a..<(a + n - 1) where idx[k + 1] != idx[k] + 1 || sec(pts[idx[k]].t, pts[idx[k + 1]].t) > mdt { fine = false }
            if !fine || !w.allSatisfy({ ok[$0] }) { continue }
            var v = Double.infinity, at = -1
            for k in w {
                let s = pts[k].spd ?? 0
                if s < v || (s == v && k < at) { v = s; at = k }
            }
            if best == nil || v > best!.v { best = (v, at) }
        }
        return best
    }

    // MARK: 3. Altitude: source, smoothing, vertical speed, station calibration

    static func baroAlt(_ p: Double) -> Double { 44330.77 * (1 - pow(p / 1013.25, 0.190263)) }

    /// The barometer if most readings have it (relative), else the GPS altitude; a missing reading holds the last.
    static func altSource(_ pts: [Pt]) -> (src: [Double], baro: Bool) {
        let nb = pts.reduce(0) { $0 + ($1.p != nil ? 1 : 0) }
        let baro = nb * 2 >= pts.count && nb > 0
        var out: [Double] = [], last: Double?
        out.reserveCapacity(pts.count)
        for q in pts {
            var v: Double?
            if baro { v = q.p.map(baroAlt) ?? last } else { v = q.alt ?? last }
            let x = v ?? 0
            out.append(x); last = x
        }
        return (out, baro)
    }

    /// The mean of vals over the points with lo <= t <= hi (ms), around index i.
    static func windowMean(_ ts: [Int64], _ vals: [Double], _ i: Int, _ lo: Double, _ hi: Double) -> Double {
        var s = 0.0, n = 0
        var j = i
        while j >= 0 && Double(ts[j]) >= lo {
            if Double(ts[j]) <= hi { s += vals[j]; n += 1 }
            j -= 1
        }
        j = i + 1
        while j < ts.count && Double(ts[j]) <= hi {
            if Double(ts[j]) >= lo { s += vals[j]; n += 1 }
            j += 1
        }
        return n > 0 ? s / Double(n) : vals[i]
    }

    static func smoothAlt(_ ts: [Int64], _ src: [Double], _ P: Params, live: Bool) -> [Double] {
        let w = P["alt", "ma_s"] * 1000
        return ts.indices.map { i in
            let t = Double(ts[i])
            return live ? windowMean(ts, src, i, t - w, t) : windowMean(ts, src, i, t - w / 2, t + w / 2)
        }
    }

    static func verticalSpeed(_ ts: [Int64], _ h: [Double], _ P: Params, live: Bool) -> [Double] {
        let w = P["alt", "vz_window_s"] * 1000
        let n = ts.count
        var out: [Double] = []
        out.reserveCapacity(n)
        var j = 0, k = 0
        for i in 0..<n {
            let t = Double(ts[i])
            let lo = t - (live ? w : w / 2), hi = live ? t : t + w / 2
            while Double(ts[j]) < lo { j += 1 }
            if k < i { k = i }
            while k + 1 < n && Double(ts[k + 1]) <= hi { k += 1 }
            let dt = sec(ts[j], ts[k])
            out.append(dt > 0 ? (h[k] - h[j]) / dt : 0)
        }
        return out
    }

    /// The vertical drop with a hysteresis threshold, over the given point indexes.
    static func drop<S: Sequence>(_ h: [Double], _ idx: S, _ P: Params) -> Double where S.Element == Int {
        let th = P["alt", "hyst_m"]
        var tot = 0.0, ref: Double?
        for i in idx {
            let v = h[i]
            if ref == nil || v > ref! + th { ref = v }   // a climb of more than the threshold moves the reference up
            else if ref! - v >= th { tot += ref! - v; ref = v }
        }
        return tot
    }

    /// [(from time, offset)] from station measurements [(time, station altitude - source altitude)]: a measurement is
    /// used within cal_jump_max_m of the last one used, or of the last one refused (two that agree win); the offset is
    /// the median of the last cal_median_n used.
    static func offsets(_ meas: [(Int64, Double)], _ P: Params) -> [(Int64, Double)] {
        let jmp = P["alt", "cal_jump_max_m"], mn = P.int("alt", "cal_median_n")
        var acc: [Double] = [], lastRej: Double?, offs: [(Int64, Double)] = []
        for (t, m) in meas {
            if acc.isEmpty || abs(m - acc[acc.count - 1]) <= jmp || (lastRej != nil && abs(m - lastRej!) <= jmp) {
                acc.append(m); lastRej = nil
                let k = acc.suffix(mn).sorted()
                let h = k.count / 2
                offs.append((t, k.count % 2 == 1 ? k[h] : (k[h - 1] + k[h]) / 2))
            } else {
                lastRej = m
            }
        }
        return offs
    }

    // MARK: 6. Map matching of a descent: Viterbi over the lines of the runs, plus "off" (-1)

    /// Per point of idx: (line index, or -1 off the runs; the distance to it).
    static func match(_ net: Net, _ pts: [Pt], _ kf: [KF], _ idx: [Int], _ P: Params, live: Bool) -> [(s: Int, d: Double)] {
        let candM = P["match", "cand_m"], sigMin = P["match", "sigma_min_m"], half = P["match", "halfwidth_m"]
        let offE = -0.5 * pow(P["match", "off_z"], 2)
        let offSwitch = P["match", "off_switch"], sw = P["match", "switch"], jump = P["match", "jump"], nearM = P["match", "near_m"]
        var prev: (s: [Int], v: [Double])?
        var bps: [[Int: Int]] = [], outLive: [Int] = [], cands: [[Int: Double]] = []
        bps.reserveCapacity(idx.count); cands.reserveCapacity(idx.count)
        for (n, i) in idx.enumerated() {
            let x = kf[i].x, y = kf[i].y
            let sig = (pow(max(sigMin, pts[i].hacc ?? 10), 2) + pow(half, 2)).squareRoot()
            let near = net.nearPistes(x, y, candM)
            var cd: [Int: Double] = [:]
            for (k, d) in near { cd[k] = d }
            cands.append(cd)
            var cs = [-1], ce = [offE]
            for (k, d) in near { cs.append(k); ce.append(-0.5 * pow(d / sig, 2)) }
            var V: [Double], bp: [Int: Int] = [:]
            if let pr = prev {
                let pk = kf[idx[n - 1]]
                V = []
                V.reserveCapacity(cs.count)
                for (si, s) in cs.enumerated() {
                    var best: Double?, arg = 0
                    for (ri, r) in pr.s.enumerated() {
                        var c: Double
                        if r == s { c = 0 }
                        else if r == -1 || s == -1 { c = offSwitch }
                        else {
                            c = sw
                            if (cands[n - 1][r] ?? 1e9) > nearM && net.pistes[r].dist(pk.x, pk.y) > nearM { c += jump }
                        }
                        let sc = pr.v[ri] - c
                        if best == nil || sc > best! || (sc == best! && r < arg) { best = sc; arg = r }
                    }
                    V.append(best! + ce[si]); bp[s] = arg
                }
                let mx = V.max()!
                V = V.map { $0 - mx }
            } else {
                V = ce
                for s in cs { bp[s] = s }
            }
            bps.append(bp)
            prev = (cs, V)
            outLive.append(argmax(cs, V))
        }
        var path: [Int]
        if live { path = outLive } else {
            path = [Int](repeating: 0, count: idx.count)
            if let pr = prev, !idx.isEmpty {
                path[idx.count - 1] = argmax(pr.s, pr.v)
                var n = idx.count - 1
                while n > 0 { path[n - 1] = bps[n][path[n]]!; n -= 1 }
            }
        }
        return path.enumerated().map { n, s in
            s == -1 ? (-1, cands[n].values.min() ?? 1e9) : (s, cands[n][s] ?? 1e9)
        }
    }

    /// The state with the highest value; the lowest state on a tie (Python's max with key (V[s], -s)).
    static func argmax(_ states: [Int], _ v: [Double]) -> Int {
        var b = 0
        for k in 1..<max(1, states.count) where v[k] > v[b] || (v[k] == v[b] && states[k] < states[b]) { b = k }
        return states[b]
    }

    /// A matched descent grouped into legs: run (names), unnamed, off, unmatched.
    static func legs(_ net: Net, _ pts: [Pt], _ kf: [KF], _ idx: [Int], _ mt: [(s: Int, d: Double)], _ P: Params) -> [Leg] {
        let minLeg = P["match", "min_leg_m"], offMin = P["match", "off_min_m"], meanMax = P["match", "leg_mean_max_m"]
        let inFrac = P["match", "leg_in_frac"], sigMin = P["match", "sigma_min_m"], half = P["match", "halfwidth_m"]
        let raw: [[String]] = idx.indices.map { n in
            let (s, _) = mt[n]
            if s == -1 { return ["off"] }
            let lab = net.label(s, kf[idx[n]].x, kf[idx[n]].y)
            return lab.isEmpty ? ["unnamed"] : ["run"] + lab
        }
        struct Pc { var key: [String]; var n0: Int; var n1: Int }
        var pieces: [Pc] = []
        for (n, k) in raw.enumerated() {
            if let l = pieces.last, l.key == k { pieces[pieces.count - 1].n1 = n } else { pieces.append(Pc(key: k, n0: n, n1: n)) }
        }
        func step(_ n: Int) -> Double { hypot(kf[idx[n]].x - kf[idx[n - 1]].x, kf[idx[n]].y - kf[idx[n - 1]].y) }
        func length(_ pc: Pc) -> Double {
            var s = 0.0
            if pc.n1 >= pc.n0 + 1 { for n in (pc.n0 + 1)...pc.n1 { s += step(n) } }
            if pc.n0 > 0 { s += step(pc.n0) }   // the step into the piece belongs to it
            return s
        }
        func mergeEqual(_ ps: [Pc]) -> [Pc] {
            var out: [Pc] = []
            for pc in ps { if let l = out.last, l.key == pc.key { out[out.count - 1].n1 = pc.n1 } else { out.append(pc) } }
            return out
        }
        // short pieces join the one before (or after, for the first)
        var changed = true
        while changed && pieces.count > 1 {
            changed = false
            for j in pieces.indices where length(pieces[j]) < minLeg {
                if j > 0 { pieces[j - 1].n1 = pieces[j].n1 } else { pieces[1].n0 = pieces[j].n0 }
                pieces.remove(at: j)
                pieces = mergeEqual(pieces)
                changed = true
                break
            }
        }
        var out: [Leg] = []
        for pc in pieces {
            let ns = pc.n0...pc.n1
            var kind = pc.key[0]
            if kind == "off" {
                let nd = ns.map { net.nearestPisteDist(kf[idx[$0]].x, kf[idx[$0]].y) }
                if nd.reduce(0, +) / Double(nd.count) < offMin { kind = "unmatched" }
            } else {
                let ds = ns.map { mt[$0].d }
                var sigIn = 0
                for n in ns {
                    let sig = (pow(max(sigMin, pts[idx[n]].hacc ?? 10), 2) + pow(half, 2)).squareRoot()
                    if mt[n].d <= 2 * sig { sigIn += 1 }
                }
                if ds.reduce(0, +) / Double(ds.count) > meanMax || Double(sigIn) < inFrac * Double(ds.count) { kind = "unmatched" }
            }
            out.append(Leg(t0: pts[idx[pc.n0]].t, t1: pts[idx[pc.n1]].t, kind: kind,
                           runs: kind == "run" ? Array(pc.key.dropFirst()) : [], distM: round(length(pc), 1)))
        }
        // equal neighbours after reclassifying
        var res: [Leg] = []
        for lg in out {
            if let l = res.last, l.kind == lg.kind, l.runs == lg.runs {
                res[res.count - 1].t1 = lg.t1; res[res.count - 1].distM = round(l.distM + lg.distM, 1)
            } else { res.append(lg) }
        }
        return res
    }

    // MARK: 9. "Here you fell" (m-8 condition 10): fun, not safety

    static let g = 9.80665

    /// (peak acceleration in g, peak rotation in rad/s) of an impact window.
    static func impactPeaks(_ e: DayJournal.Event) -> (Double, Double) {
        func peak(_ a: [Double]) -> Double {
            var m = 0.0, i = 0
            while i < a.count - 2 { m = max(m, (a[i] * a[i] + a[i + 1] * a[i + 1] + a[i + 2] * a[i + 2]).squareRoot()); i += 3 }
            return m
        }
        return (peak(e.acc ?? []) / g, peak(e.gyr ?? []))
    }

    /// A blow of at least trigger_g and rot_min_radps, not on a lift, going at least v_before_mps in the before_s before
    /// it, and at most v_after_mps through still_s after settle_s. A dismissed blow never counts. [(t, known at)].
    static func falls(_ pts: [Pt], _ ts: [Int64], _ events: [DayJournal.Event], _ act: [Int], _ P: Params) -> [(t: Int64, at: Int64)] {
        let trig = P["fall", "trigger_g"], rot = P["fall", "rot_min_radps"], before = P["fall", "before_s"]
        let settle = P["fall", "settle_s"], still = P["fall", "still_s"], vBefore = P["fall", "v_before_mps"]
        let vAfter = P["fall", "v_after_mps"], debounce = P["fall", "debounce_s"]
        let impacts = events.enumerated().filter { $0.element.ev == .impact }
            .sorted { ($0.element.t, $0.offset) < ($1.element.t, $1.offset) }.map(\.element)
        // "I did not fall" is a second line with the same t (the journal is append-only); it cancels the blow
        let gone = Set(impacts.filter { $0.dismissed == true }.map(\.t))
        var out: [(t: Int64, at: Int64)] = []
        for e in impacts {
            if e.dismissed == true || e.acc == nil || gone.contains(e.t) { continue }
            let (pa, pw) = impactPeaks(e)
            if pa < trig || pw < rot { continue }
            let t = e.t, td = Double(t)
            guard let i = ts.lastIndex(where: { $0 <= t }), act[i] < 4 else { continue }
            let bef = ts.indices.compactMap { k -> Double? in
                td - before * 1000 <= Double(ts[k]) && ts[k] <= t ? pts[k].spd : nil
            }
            let a0 = td + settle * 1000, a1 = td + (settle + still) * 1000
            let aft = ts.indices.filter { a0 <= Double(ts[$0]) && Double(ts[$0]) <= a1 }.map { pts[$0].spd }
            if bef.isEmpty || bef.max()! < vBefore { continue }
            if Double(aft.count) < still - 1 || aft.contains(where: { $0 == nil || $0! > vAfter }) { continue }
            if let l = out.last, Double(t - l.t) < debounce * 1000 { continue }
            out.append((t, Int64(a1)))
        }
        return out
    }

    // MARK: 10. Timing one descent: when it stops by itself

    /// From live values only: on a lift; still near a lift's boarding station for station_still_s; or still anywhere
    /// for still_s. Only after going down min_drop_m and skiing min_ski_s.
    static func autostop(_ net: Net, _ pts: [Pt], _ ts: [Int64], _ kf: [KF], _ act: [Int], _ P: Params) -> Live.Autostop? {
        let minDrop = P["autostop", "min_drop_m"], minSki = P["autostop", "min_ski_s"], stillV = P["autostop", "still_mps"]
        let stM = P["autostop", "station_m"], stStill = P["autostop", "station_still_s"], stillS = P["autostop", "still_s"]
        let boards = net.lifts.flatMap { l in l.stations.dropLast().map { l.line.at($0.u) } }
        var top: Double?, h: Double?, skiS = 0.0, stillFrom: Int?, prev: Int?
        for i in pts.indices {
            let a = act[i]
            if let z = pts[i].p.map(baroAlt) ?? pts[i].alt {
                top = max(top ?? z, z); h = z
            }
            if let pv = prev, act[pv] == ski { skiS += sec(ts[pv], ts[i]) }
            prev = i
            guard let tp = top, let hh = h, tp - hh >= minDrop, skiS >= minSki else { stillFrom = nil; continue }
            if a >= 4 { return .init(t: ts[i], reason: "lift") }
            if let s = pts[i].spd, s <= stillV {
                if stillFrom == nil { stillFrom = i }
                let dur = sec(ts[stillFrom!], ts[i])
                let x = kf[i].x, y = kf[i].y
                let near = boards.contains { hypot(x - $0.x, y - $0.y) <= stM }
                if near && dur >= stStill { return .init(t: ts[i], reason: "station") }
                if dur >= stillS { return .init(t: ts[i], reason: "still") }
            } else {
                stillFrom = nil
            }
        }
        return nil
    }
}
