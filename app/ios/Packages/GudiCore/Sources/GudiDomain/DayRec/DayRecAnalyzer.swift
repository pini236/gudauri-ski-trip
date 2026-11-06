import Foundation

extension DayRec {
    /// The analysis of a day (analyze.py's Analyzer): `final` sees the whole day (smoothed backwards, full Viterbi) and
    /// is the truth of the day; `live` is causal, every value at a time from the readings up to it.
    public struct Analyzer: Sendable {
        public let net: Net
        public let params: Params

        public init(net: Net, params: Params) { self.net = net; self.params = params }

        struct Prep {
            var xy: [(x: Double, y: Double)], kf: [KF], ts: [Int64], src: [Double], baro: Bool, h: [Double], vz: [Double]
        }

        func prep(_ pts: [Pt], live: Bool) -> Prep {
            let P = params
            let xy = pts.map { net.proj.xy($0.lat, $0.lon) }
            let kf = kalman(xy, pts, P, smooth: !live)
            let ts = pts.map(\.t)
            let (src, baro) = altSource(pts)
            let h = smoothAlt(ts, src, P, live: live)
            let vz = verticalSpeed(ts, h, P, live: live)
            return Prep(xy: xy, kf: kf, ts: ts, src: src, baro: baro, h: h, vz: vz)
        }

        // MARK: live

        /// The live view as it changes, and the live maximum speed.
        public func live(_ day: DayJournal.Day) -> Live {
            let pts = day.points, P = params
            guard !pts.isEmpty else { return Live(events: [], maxKmh: nil, falls: [], autostop: nil) }
            let pr = prep(pts, live: true)
            let (E, feats) = emissions(pts, pr.kf, pr.vz)
            var act = viterbiActivity(E, feats, live: true)
            let car = driveMask(net, pts, pr.kf, pr.ts, act, P, live: true)
            for i in act.indices where car[i] { act[i] = drive }
            let mt = match(net, pts, pr.kf, Array(pts.indices), P, live: true)
            var evs: [Live.Change] = []
            var last: (String, String?, [String]?)?
            for (i, a) in act.enumerated() {
                var ev = Live.Change(t: pr.ts[i], act: actName(a))
                if a >= 4 { ev.lift = net.lifts[a - 4].id }
                if a == ski {
                    let s = mt[i].s
                    ev.runs = s >= 0 ? net.label(s, pr.kf[i].x, pr.kf[i].y) : nil
                }
                let key = (ev.act, ev.lift, ev.runs)
                if last == nil || last!.0 != key.0 || last!.1 != key.1 || last!.2 != key.2 { evs.append(ev); last = key }
            }
            // the live maximum: a reading counts once its window is complete, only while skiing
            let ok = validSpeeds(pts, pr.xy, P)
            let best = maxSpeed(pts, ok, act.indices.filter { act[$0] == ski }, P)
            var out = Live(events: evs, maxKmh: best.map { kmh($0.v) },
                           falls: falls(pts, pr.ts, day.events, act, P).map { Live.Fall(t: $0.t, at: $0.at) })
            if day.header.mode == "run" { out.autostop = autostop(net, pts, pr.ts, pr.kf, act, P) }
            return out
        }

        // MARK: final

        public func final(_ day: DayJournal.Day) -> Analysis {
            let pts = day.points, P = params
            var res = Analysis(analysis: P.analysis, v: day.header.v, resort: day.header.resort, mode: day.header.mode ?? "day",
                               points: pts.count, gaps: [], falls: [], segments: [], stats: nil)
            guard !pts.isEmpty else { return res }
            let pr = prep(pts, live: false)
            let ts = pr.ts, kf = pr.kf
            let (E, feats) = emissions(pts, kf, pr.vz)
            var path = viterbiActivity(E, feats, live: false)
            let car = driveMask(net, pts, kf, ts, path, P, live: false)
            for i in path.indices where car[i] { path[i] = drive }
            let pcs = pieces(path, ts, kf, feats, pts, pr.h)
            var segs = segments(pcs, pts, pr.xy, kf, ts, pr.h)
            var state = [Int](repeating: 0, count: pts.count)
            for p in pcs where p.i1 >= p.i0 { for i in p.i0...p.i1 { state[i] = p.s } }
            res.falls = falls(pts, ts, day.events, state, P).map { f in
                let i = ts.lastIndex { $0 <= f.t }!
                let ll = net.proj.ll(kf[i].x, kf[i].y)
                return Spot(t: f.t, lat: round(ll.lat, 6), lon: round(ll.lon, 6))
            }
            calibrate(&segs, ts, pr.src)
            res.start = ts[0]; res.end = ts[ts.count - 1]; res.baro = pr.baro
            res.coverage = round(Double(pts.count) / (sec(ts[0], ts[ts.count - 1]) + 1), 4)
            res.gaps = (1..<max(1, ts.count)).filter { Double(ts[$0] - ts[$0 - 1]) > P.gapS * 1000 }.map { Span(t0: ts[$0 - 1], t1: ts[$0]) }
            res.segments = segs.map(\.out)
            res.stats = stats(res.segments)
            return res
        }

        // MARK: 4. Activity: Viterbi over stand, walk, ski, up and one state per lift

        typealias Feat = (d: Double, u: Double, ux: Double, uy: Double)

        /// The lifts close enough to (x, y): distance, position along the cable, and the cable's direction there.
        func liftFeatures(_ x: Double, _ y: Double) -> [Int: Feat] {
            var out: [Int: Feat] = [:]
            let m = params["lift", "max_dist_m"]
            for (li, l) in net.lifts.enumerated() {
                let b = l.line.box
                if x < b.0 - m || x > b.2 + m || y < b.1 - m || y > b.3 + m { continue }
                let n = l.line.nearest(x, y)
                if n.d <= m { let a = l.line.at(n.s); out[li] = (n.d, n.s, a.ux, a.uy) }
            }
            return out
        }

        func emissions(_ pts: [Pt], _ kf: [KF], _ vz: [Double]) -> ([[Double]], [[Int: Feat]]) {
            let P = params
            let lvlS = P["activity", "level_vz_sigma_mps"], cap = P["activity", "motion_cap"]
            let mLow = P["activity", "motion_low"], mHigh = P["activity", "motion_high"], mSig = P["activity", "motion_sigma"]
            let standS = P["activity", "stand_sigma_mps"], walkM = P["activity", "walk_mean_mps"], walkS = P["activity", "walk_sigma_mps"]
            let skiMin = P["activity", "ski_min_mps"], skiS = P["activity", "ski_sigma_mps"], skiVz = P["activity", "ski_vz_sigma_mps"]
            let upVz = P["activity", "up_vz_mps"], upS = P["activity", "up_vz_sigma_mps"], upCost = P["activity", "up_cost"]
            let sigM = P["lift", "sigma_m"], lo = P["lift", "speed_lo"], hi = P["lift", "speed_hi"], rel = P["lift", "speed_rel_sigma"]
            let cross = P["lift", "cross_sigma_mps"], vzMin = P["lift", "vz_min_mps"], vzS = P["lift", "vz_sigma_mps"]
            let nl = net.lifts.count
            var E: [[Double]] = [], feats: [[Int: Feat]] = []
            E.reserveCapacity(pts.count); feats.reserveCapacity(pts.count)
            for (i, pt) in pts.enumerated() {
                let k = kf[i]
                let vh = hypot(k.vx, k.vy), z = vz[i]
                let lvl = -0.5 * pow(z / lvlS, 2)
                let mlow = pt.mv.map { -min(cap, 0.5 * pow($0 / mLow, 2)) } ?? 0
                let mhigh = pt.mv.map { -min(cap, 0.5 * pow(max(0, mHigh - $0) / mSig, 2)) } ?? 0
                var e = [Double](repeating: 0, count: 4 + nl)
                e[stand] = -0.5 * pow(vh / standS, 2) + lvl + mlow
                e[walk] = -0.5 * pow((vh - walkM) / walkS, 2) + lvl + mhigh
                e[ski] = -0.5 * pow(max(0, skiMin - vh) / skiS, 2) - 0.5 * pow(max(0, z) / skiVz, 2) + mhigh
                e[up] = -0.5 * pow(max(0, upVz - z) / upS, 2) - upCost
                let f = liftFeatures(k.x, k.y)
                feats.append(f)
                let sd = max(sigM, pt.hacc ?? 10)
                for li in 0..<nl {
                    guard let ft = f[li] else { e[4 + li] = neg; continue }
                    let sp = net.lifts[li].speed
                    let va = k.vx * ft.ux + k.vy * ft.uy
                    let vc = -k.vx * ft.uy + k.vy * ft.ux
                    // the speed in the data is a guide, not a rule: lifts slow down and the data can be old
                    let over = max(0, lo * sp - va, va - hi * sp)
                    e[4 + li] = -0.5 * pow(ft.d / sd, 2) - 0.5 * pow(over / (rel * sp), 2)
                        - 0.5 * pow(vc / cross, 2)
                        - 0.5 * pow(max(0, vzMin - z) / vzS, 2) + mlow
                }
                E.append(e)
            }
            return (E, feats)
        }

        func boardOK(_ li: Int, _ f: [Int: Feat]) -> Bool {
            guard let u = f[li]?.u else { return false }
            let m = params["lift", "board_m"]
            return net.lifts[li].stations.dropLast().contains { abs(u - $0.u) <= m }
        }

        func viterbiActivity(_ E: [[Double]], _ feats: [[Int: Feat]], live: Bool) -> [Int] {
            let T = E.count
            guard T > 0 else { return [] }
            let S = E[0].count
            let sw = params["activity", "switch"], far = params["activity", "board_far"]
            func best(_ V: [Double]) -> Int {
                var b = 0
                for s in 1..<S where V[s] > V[b] { b = s }
                return b
            }
            var V = E[0]
            var bps: [[Int]] = [], cur = [best(V)]
            bps.reserveCapacity(T)
            for t in 1..<T {
                // the best and second best of the previous step
                var b1 = -1, b2 = -1
                for s in 0..<S {
                    if b1 < 0 || V[s] > V[b1] { b2 = b1; b1 = s }
                    else if b2 < 0 || V[s] > V[b2] { b2 = s }
                }
                var nv = [Double](repeating: 0, count: S), bp = [Int](repeating: 0, count: S)
                for s in 0..<S {
                    let o = b1 != s ? b1 : b2
                    var swv = V[o] - sw
                    if s >= 4 && !boardOK(s - 4, feats[t]) { swv -= far }
                    if V[s] >= swv { nv[s] = V[s] + E[t][s]; bp[s] = s } else { nv[s] = swv + E[t][s]; bp[s] = o }
                }
                let mx = nv.max()!
                V = nv.map { $0 - mx }
                bps.append(bp)
                cur.append(best(V))
            }
            if live { return cur }
            var path = [Int](repeating: 0, count: T)
            path[T - 1] = best(V)
            var t = T - 1
            while t > 0 { path[t - 1] = bps[t - 1][path[t]]; t -= 1 }
            return path
        }

        // MARK: 5. Pieces of the day, to the second

        struct Pc { var s: Int, i0: Int, i1: Int }

        func mergeEqual(_ ps: [Pc]) -> [Pc] {
            var out: [Pc] = []
            for p in ps { if let l = out.last, l.s == p.s { out[out.count - 1].i1 = p.i1 } else { out.append(p) } }
            return out
        }

        func pieces(_ path: [Int], _ ts: [Int64], _ kf: [KF], _ feats: [[Int: Feat]], _ pts: [Pt], _ h: [Double]) -> [Pc] {
            let P = params
            let pauseMax = P["lift", "pause_max_s"], snap = P["lift", "station_snap_m"], minProg = P["lift", "min_progress_m"]
            let edgeMps = P["lift", "edge_mps"], hold = P.int("lift", "edge_hold")
            let skiMin = P["segments", "ski_min_s"], minPiece = P["segments", "min_piece_s"], flicker = P["segments", "flicker_s"]
            let hyst = P["alt", "hyst_m"]
            var pcs: [Pc] = []
            for (i, s) in path.enumerated() {
                if let l = pcs.last, l.s == s { pcs[pcs.count - 1].i1 = i } else { pcs.append(Pc(s: s, i0: i, i1: i)) }
            }
            func dur(_ p: Pc) -> Double { sec(ts[p.i0], ts[p.i1]) }
            func u(_ i: Int, _ li: Int) -> Double { net.lifts[li].line.nearest(kf[i].x, kf[i].y).s }
            let quietS = [stand, walk, up]
            // a lift stopped for a while, or the signal wandered: the same lift again within pause_max_s, with no
            // skiing in between and not further down the cable, is one ride
            var j = 0
            while j < pcs.count {
                let a = pcs[j]
                if a.s < 4 { j += 1; continue }
                var k = j + 1
                while k < pcs.count && quietS.contains(pcs[k].s) && sec(ts[a.i1], ts[pcs[k].i1]) <= pauseMax { k += 1 }
                if k > j + 1 && k < pcs.count && pcs[k].s == a.s && sec(ts[a.i1], ts[pcs[k].i0]) <= pauseMax
                    && u(pcs[k].i0, a.s - 4) >= u(a.i1, a.s - 4) - snap {
                    pcs[j].i1 = pcs[k].i1
                    pcs.removeSubrange((j + 1)...k)
                } else { j += 1 }
            }
            // a "ride" that does not climb along the cable is not a ride
            for n in pcs.indices where pcs[n].s >= 4 {
                let p = pcs[n], li = p.s - 4
                let u0 = feats[p.i0][li]?.u, u1 = feats[p.i1][li]?.u
                if u0 == nil || u1 == nil || u1! - u0! < minProg {
                    let vh = (p.i0...p.i1).reduce(0.0) { $0 + hypot(kf[$1].vx, kf[$1].vy) } / Double(p.i1 - p.i0 + 1)
                    pcs[n].s = vh < 0.5 ? stand : up
                }
            }
            pcs = mergeEqual(pcs)
            // the edges of a ride, to the second: from where the position along the cable starts to grow to where it stops
            var out: [Pc] = []
            for p in pcs {
                if p.s < 4 { out.append(p); continue }
                let line = net.lifts[p.s - 4].line
                let idx = Array(p.i0...p.i1)
                let us = idx.map { line.nearest(kf[$0].x, kf[$0].y).s }
                let mv: [Bool] = us.count < 2 ? [] : (0..<(us.count - 1)).map { k in
                    (us[k + 1] - us[k]) / max(0.001, sec(ts[idx[k]], ts[idx[k + 1]])) >= edgeMps
                }
                let starts = mv.count - hold + 1 > 0 ? (0..<(mv.count - hold + 1)).filter { k in mv[k..<(k + hold)].allSatisfy { $0 } } : []
                guard let kst = starts.first, let kl = starts.last else { out.append(p); continue }
                let a = p.i0 + kst, b = p.i0 + kl + hold
                if a > p.i0 { out.append(quiet(p.i0, a - 1, kf)) }
                out.append(Pc(s: p.s, i0: a, i1: b))
                if b < p.i1 { out.append(quiet(b + 1, p.i1, kf)) }
            }
            pcs = mergeEqual(out)
            // a few seconds of "ski" that do not go down are the filter slowing down (off a lift), not skiing
            for n in pcs.indices where pcs[n].s == ski && dur(pcs[n]) < skiMin && drop(h, pcs[n].i0...max(pcs[n].i0, pcs[n].i1), P) < hyst {
                pcs[n].s = walk
            }
            pcs = mergeEqual(pcs)
            // the edges of skiing, to the second: where the raw Doppler speed crosses ski_edge_mps (held edge_hold readings)
            if pcs.count > 1 {
                for j in 0..<(pcs.count - 1) {
                    let a = pcs[j], b = pcs[j + 1]
                    if (a.s == stand || a.s == walk) && b.s == ski {
                        if let k = edge(pts, ts, b.i0, a.i0, b.i1, up: true) { pcs[j].i1 = k - 1; pcs[j + 1].i0 = k }
                    } else if a.s == ski && (b.s == stand || b.s == walk) {
                        if let k = edge(pts, ts, a.i1, a.i0, b.i1, up: false) { pcs[j].i1 = k; pcs[j + 1].i0 = k + 1 }
                    }
                }
            }
            // skiing next to a lift (straight off it, or straight onto it): the slow seconds become a stop or a walk
            out = []
            for (j, p0) in pcs.enumerated() {
                var p = p0
                if p.s != ski { out.append(p); continue }
                if j > 0 && pcs[j - 1].s >= 4, let k = edge(pts, ts, p.i0, p.i0 - 1, p.i1, up: true), k > p.i0 {
                    out.append(quiet(p.i0, k - 1, kf)); p.i0 = k
                }
                var tail: Pc?
                if j + 1 < pcs.count && pcs[j + 1].s >= 4, let k = edge(pts, ts, p.i1, p.i0, p.i1, up: false), k < p.i1 {
                    tail = quiet(k + 1, p.i1, kf); p.i1 = k
                }
                out.append(p)
                if let tail { out.append(tail) }
            }
            pcs = mergeEqual(out.filter { $0.i1 >= $0.i0 })
            // short stand, walk and up pieces join a neighbouring stand, walk or up piece. Never a lift or a descent:
            // their edges are the moments the state changed, to the second. Between a lift and a descent a short piece
            // stays (a real stop at the top), unless it is a flicker shorter than flicker_s.
            var changed = true
            while changed && pcs.count > 1 {
                changed = false
                for j in pcs.indices {
                    let p = pcs[j]
                    if !quietS.contains(p.s) || dur(p) >= minPiece { continue }
                    let hasPrev = j > 0, hasNext = j + 1 < pcs.count
                    if hasPrev && quietS.contains(pcs[j - 1].s) {
                        if dur(p) > dur(pcs[j - 1]) { pcs[j - 1].s = p.s }   // the longer of the two names the joined piece
                        pcs[j - 1].i1 = p.i1
                    } else if hasNext && quietS.contains(pcs[j + 1].s) {
                        if dur(p) > dur(pcs[j + 1]) { pcs[j + 1].s = p.s }
                        pcs[j + 1].i0 = p.i0
                    } else if dur(p) < flicker && hasPrev {
                        pcs[j - 1].i1 = p.i1
                    } else if dur(p) < flicker && hasNext {
                        pcs[j + 1].i0 = p.i0
                    } else { continue }
                    pcs.remove(at: j)
                    pcs = mergeEqual(pcs)
                    changed = true
                    break
                }
            }
            return pcs
        }

        func quiet(_ i0: Int, _ i1: Int, _ kf: [KF]) -> Pc {
            let vh = (i0...i1).reduce(0.0) { $0 + hypot(kf[$1].vx, kf[$1].vy) } / Double(i1 - i0 + 1)
            return Pc(s: vh < params["activity", "walk_mean_mps"] / 2 ? stand : walk, i0: i0, i1: i1)
        }

        /// The first index (up: skiing starts) or the last (down: it ends) near i, within edge_window_s, where the raw
        /// Doppler speed is at least ski_edge_mps for edge_hold readings in a row.
        func edge(_ pts: [Pt], _ ts: [Int64], _ i: Int, _ lo: Int, _ hi: Int, up isUp: Bool) -> Int? {
            let w = params["segments", "edge_window_s"] * 1000, v = params["segments", "ski_edge_mps"]
            let n = params.int("segments", "edge_hold")
            func fast(_ k: Int) -> Bool { (pts[k].spd ?? -1) >= v && pts[k].spd != nil }
            guard hi >= lo + 1 else { return nil }
            let cands = ((lo + 1)...hi).filter { abs(Double(ts[$0] - ts[i])) <= w }
            if isUp {
                for k in cands where k + n - 1 <= hi && (k..<(k + n)).allSatisfy(fast) { return k }
            } else {
                for k in cands.reversed() where k - n + 1 >= lo && ((k - n + 1)...k).allSatisfy(fast) { return k }
            }
            return nil
        }

        // MARK: segments

        struct Work {
            var out: Segment
            var i0: Int, i1: Int
            var li = -1
            var group: [Pc] = []
        }

        func segments(_ pcs: [Pc], _ pts: [Pt], _ xy: [(x: Double, y: Double)], _ kf: [KF], _ ts: [Int64], _ h: [Double]) -> [Work] {
            let P = params
            let restMin = P["segments", "rest_min_s"], upIn = P["segments", "up_in_descent_s"]
            let minDrop = P["segments", "min_descent_drop_m"], minDesc = P["segments", "min_descent_m"]
            let queueGap = P["segments", "queue_gap_s"], queueDist = P["segments", "queue_dist_m"], longBreak = P["segments", "long_break_s"]
            func dur(_ p: Pc) -> Double { sec(ts[p.i0], ts[p.i1]) }
            let descent = -99
            // descents: ski pieces joined across short stands and walks
            var groups: [(kind: Int, ps: [Pc])] = []
            var j = 0
            while j < pcs.count {
                let p = pcs[j]
                if p.s == ski {
                    var g = [p], k = j + 1
                    while k < pcs.count {
                        let q = pcs[k]
                        if q.s == ski { g.append(q); k += 1; continue }
                        if ((q.s == stand || q.s == walk) && dur(q) < restMin || q.s == up && dur(q) < upIn)
                            && k + 1 < pcs.count && pcs[k + 1].s == ski { g.append(q); k += 1; continue }
                        break
                    }
                    groups.append((descent, g)); j = k
                } else { groups.append((p.s, [p])); j += 1 }
            }
            func work(_ type: String, _ i0: Int, _ i1: Int) -> Work { Work(out: Segment(type: type, t0: ts[i0], t1: ts[i1]), i0: i0, i1: i1) }
            var segs: [Work] = []
            for (kind, g) in groups {
                let i0 = g[0].i0, i1 = g[g.count - 1].i1
                if kind == descent {
                    let idx = Array(i0...i1)
                    if drop(h, idx, P) < minDrop && dist(kf, ts, idx) < minDesc { segs.append(work("walk", i0, i1)); continue }
                    var w = work("descent", i0, i1); w.group = g; segs.append(w)
                } else if kind == drive {
                    segs.append(work("drive", i0, i1))
                } else if kind >= 4 {
                    var w = work("lift", i0, i1); w.li = kind - 4; segs.append(w)
                } else {
                    segs.append(work(kind == up ? "ascent" : kind == walk ? "walk" : "stop", i0, i1))
                }
            }
            // neighbouring walks (a short descent that became a walk) join
            var joined: [Work] = []
            for s in segs {
                if let l = joined.last, s.out.type == "walk", l.out.type == "walk" {
                    joined[joined.count - 1].i1 = s.i1
                } else { joined.append(s) }
            }
            segs = joined
            let ok = validSpeeds(pts, xy, P)
            for n in segs.indices {
                let i0 = segs[n].i0, i1 = segs[n].i1
                var s = segs[n].out
                s.t0 = ts[i0]; s.t1 = ts[i1]
                let idx = Array(i0...i1)
                switch s.type {
                case "lift":
                    let lift = net.lifts[segs[n].li]
                    let u0 = lift.line.nearest(kf[i0].x, kf[i0].y).s, u1 = lift.line.nearest(kf[i1].x, kf[i1].y).s
                    s.lift = lift.id; s.name = lift.name
                    s.from = station(lift, u0); s.to = station(lift, u1)
                    s.riseM = round(lift.eleAt(u1) - lift.eleAt(u0), 1)
                case "descent":
                    let g = segs[n].group
                    let skiIdx = g.filter { $0.s == ski }.flatMap { Array($0.i0...$0.i1) }
                    let mt = match(net, pts, kf, idx, P, live: false)
                    s.legs = legs(net, pts, kf, idx, mt, P)
                    s.distM = round(dist(kf, ts, idx), 1)
                    s.dropM = round(drop(h, idx, P), 1)
                    let mov = g.filter { $0.s == ski }.reduce(0.0) { $0 + sec(ts[$1.i0], ts[$1.i1]) }
                    s.movingS = pyRound(mov)
                    s.avgKmh = mov > 0 ? kmh(s.distM! / mov) : nil
                    if let best = maxSpeed(pts, ok, skiIdx, P) {
                        let ll = net.proj.ll(kf[best.at].x, kf[best.at].y)
                        s.maxKmh = kmh(best.v)
                        s.maxAt = Spot(t: ts[best.at], lat: round(ll.lat, 6), lon: round(ll.lon, 6))
                    }
                    s.stops = g.filter { $0.s != ski }.map { Span(t0: ts[$0.i0], t1: ts[$0.i1]) }
                    s.offpiste = offpiste(kf, ts, h, idx)
                case "stop":
                    let mx = idx.reduce(0.0) { $0 + kf[$1].x } / Double(idx.count), my = idx.reduce(0.0) { $0 + kf[$1].y } / Double(idx.count)
                    let ll = net.proj.ll(mx, my)
                    s.lat = round(ll.lat, 6); s.lon = round(ll.lon, 6)
                    var kind = "rest"
                    if n + 1 < segs.count, segs[n + 1].out.type == "lift", sec(ts[i1], ts[segs[n + 1].i0]) <= queueGap {
                        let lift = net.lifts[segs[n + 1].li]
                        let u = lift.line.nearest(mx, my).s
                        var st = lift.stations[0]
                        for q in lift.stations.dropLast() where abs(q.u - u) < abs(st.u - u) { st = q }
                        let pt = lift.line.at(st.u)
                        if hypot(mx - pt.x, my - pt.y) <= queueDist { kind = "queue" }
                    }
                    if kind == "rest" && sec(ts[i0], ts[i1]) >= longBreak { kind = "long" }
                    s.kind = kind
                case "walk":
                    s.distM = round(dist(kf, ts, idx), 1)
                case "ascent":
                    s.riseM = round(h[i1] - h[i0], 1)
                case "drive":
                    s.distM = round(dist(kf, ts, idx), 1)
                    s.riseM = round(h[i1] - h[i0], 1)
                default: break
                }
                segs[n].out = s
            }
            return segs
        }

        /// Off-piste events in a descent: a stretch going down at least drop_m, of which at least far_frac of the
        /// readings are more than far_m from every run and road; stretches of far readings join across bridge_s.
        func offpiste(_ kf: [KF], _ ts: [Int64], _ h: [Double], _ idx: [Int]) -> [OffPiste] {
            let P = params
            let farM = P["offpiste", "far_m"], frac = P["offpiste", "far_frac"], dropM = P["offpiste", "drop_m"], bridge = P["offpiste", "bridge_s"]
            let far = idx.map { net.nearestPisteDist(kf[$0].x, kf[$0].y) > farM && net.nearestRoadDist(kf[$0].x, kf[$0].y) > farM }
            var runs: [(Int, Int)] = []
            var n = 0
            while n < idx.count {
                if far[n] {
                    var m = n
                    while m + 1 < idx.count && far[m + 1] { m += 1 }
                    if let l = runs.last, sec(ts[idx[l.1]], ts[idx[n]]) <= bridge { runs[runs.count - 1].1 = m } else { runs.append((n, m)) }
                    n = m + 1
                } else { n += 1 }
            }
            var out: [OffPiste] = []
            for (a, b) in runs {
                let sub = Array(idx[a...b])
                let f = Double(far[a...b].filter { $0 }.count) / Double(sub.count)
                let dr = drop(h, sub, P)
                if f >= frac && dr >= dropM {
                    out.append(OffPiste(t0: ts[sub[0]], t1: ts[sub[sub.count - 1]], dropM: round(dr, 1), distM: round(dist(kf, ts, sub), 1)))
                }
            }
            return out
        }

        /// The station nearest along the cable, if within station_snap_m; else unmapped.
        func station(_ lift: Lift, _ u: Double) -> StationRef {
            var st = lift.stations[0]
            for q in lift.stations where abs(q.u - u) < abs(st.u - u) { st = q }
            return StationRef(station: abs(st.u - u) <= params["lift", "station_snap_m"] ? st.id : nil)
        }

        func dist(_ kf: [KF], _ ts: [Int64], _ idx: [Int]) -> Double {
            var s = 0.0
            if idx.count > 1 {
                for k in 1..<idx.count where sec(ts[idx[k - 1]], ts[idx[k]]) <= params.resetGapS {
                    s += hypot(kf[idx[k]].x - kf[idx[k - 1]].x, kf[idx[k]].y - kf[idx[k - 1]].y)
                }
            }
            return s
        }

        /// Absolute altitude: offsets measured at known stations (median, jump guard), applied from then on.
        func calibrate(_ segs: inout [Work], _ ts: [Int64], _ src: [Double]) {
            let w = params["alt", "cal_window_s"] * 1000 / 2
            var meas: [(Int64, Double)] = []
            for s in segs where s.out.type == "lift" {
                let lift = net.lifts[s.li]
                for (ref, i) in [(s.out.from, s.i0), (s.out.to, s.i1)] {
                    guard let sid = ref?.station, let st = lift.stations.first(where: { $0.id == sid }) else { continue }
                    meas.append((ts[i], st.ele - windowMean(ts, src, i, Double(ts[i]) - w, Double(ts[i]) + w)))
                }
            }
            let offs = offsets(meas, params)
            func alt(_ i: Int) -> Double? {
                guard var o = offs.first?.1 else { return nil }
                for (t, v) in offs where t <= ts[i] { o = v }
                return round(src[i] + o, 1)
            }
            for n in segs.indices where segs[n].out.type == "descent" {
                segs[n].out.topM = alt(segs[n].i0); segs[n].out.bottomM = alt(segs[n].i1)
            }
        }

        func stats(_ segs: [Segment]) -> Stats {
            let d = segs.filter { $0.type == "descent" }, l = segs.filter { $0.type == "lift" }
            var best: Segment?
            for s in d where s.maxKmh != nil { if best == nil || s.maxKmh! > best!.maxKmh! { best = s } }
            return Stats(descents: d.count, lifts: l.count,
                         skiDistM: round(d.reduce(0.0) { $0 + ($1.distM ?? 0) }, 1),
                         dropM: round(d.reduce(0.0) { $0 + ($1.dropM ?? 0) }, 1),
                         skiS: d.reduce(0) { $0 + ($1.movingS ?? 0) },
                         liftS: pyRound(l.reduce(0.0) { $0 + sec($1.t0, $1.t1) }),
                         liftRiseM: round(l.reduce(0.0) { $0 + ($1.riseM ?? 0) }, 1),
                         maxKmh: best?.maxKmh, maxAt: best?.maxAt)
        }
    }
}
