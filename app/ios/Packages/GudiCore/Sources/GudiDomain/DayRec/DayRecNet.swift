import Foundation
import GudiData

extension DayRec {
    /// Local metres, exactly as site/js/app.js (net.py's Proj): x east, y south, around the resort's own `proj` in
    /// terrain.json (for Gudauri the same point as `Geo`).
    public struct Proj: Sendable {
        public let lat0: Double, lon0: Double
        let kx: Double
        static let km = 111320.0

        public init(lat0: Double, lon0: Double) {
            self.lat0 = lat0; self.lon0 = lon0
            kx = Self.km * cos(lat0 * Double.pi / 180)
        }

        public func xy(_ lat: Double, _ lon: Double) -> (x: Double, y: Double) { ((lon - lon0) * kx, -(lat - lat0) * Self.km) }
        public func ll(_ x: Double, _ y: Double) -> (lat: Double, lon: Double) { (lat0 - y / Self.km, lon0 + x / kx) }
    }

    /// A polyline in local metres, with distance and position along it.
    struct Line: Sendable {
        let xs: [Double], ys: [Double]
        let cum: [Double]
        let length: Double
        /// min x, min y, max x, max y
        let box: (Double, Double, Double, Double)

        init(_ pts: [(x: Double, y: Double)]) {
            xs = pts.map(\.x); ys = pts.map(\.y)
            var c = [0.0]
            for i in 1..<max(1, pts.count) { c.append(c[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])) }
            cum = c; length = c[c.count - 1]
            box = (xs.min() ?? 0, ys.min() ?? 0, xs.max() ?? 0, ys.max() ?? 0)
        }

        var count: Int { xs.count }

        /// Distance, position along the line, and the nearest point.
        func nearest(_ x: Double, _ y: Double) -> (d: Double, s: Double, x: Double, y: Double) {
            if xs.count == 1 { return (hypot(x - xs[0], y - ys[0]), 0, xs[0], ys[0]) }
            var best = (d: 1e18, s: 0.0, x: xs[0], y: ys[0])
            for i in 0..<(xs.count - 1) {
                let ax = xs[i], ay = ys[i], dx = xs[i + 1] - ax, dy = ys[i + 1] - ay
                let l2 = dx * dx + dy * dy
                let t = l2 == 0 ? 0.0 : max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / l2))
                let qx = ax + t * dx, qy = ay + t * dy
                let d = hypot(x - qx, y - qy)
                if d < best.d { best = (d, cum[i] + t * l2.squareRoot(), qx, qy) }
            }
            return best
        }

        /// Point and unit direction at position s along the line.
        func at(_ s0: Double) -> (x: Double, y: Double, ux: Double, uy: Double) {
            let s = max(0.0, min(length, s0))
            if xs.count >= 2 {
                for i in 0..<(xs.count - 1) where cum[i + 1] >= s || i == xs.count - 2 {
                    let seg = cum[i + 1] - cum[i]
                    let t = seg == 0 ? 0.0 : (s - cum[i]) / seg
                    let ux = xs[i + 1] - xs[i], uy = ys[i + 1] - ys[i]
                    var n = hypot(ux, uy)
                    if n == 0 { n = 1 }
                    return (xs[i] + t * ux, ys[i] + t * uy, ux / n, uy / n)
                }
            }
            return (xs[0], ys[0], 1, 0)
        }

        func reversed() -> Line { Line(Array(zip(xs, ys).map { (x: $0.0, y: $0.1) }.reversed())) }

        func contains(_ x: Double, _ y: Double) -> Bool {
            var inside = false
            var j = xs.count - 1
            for i in 0..<xs.count {
                let xi = xs[i], yi = ys[i], xj = xs[j], yj = ys[j]
                if (yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi { inside.toggle() }
                j = i
            }
            return inside
        }
    }

    /// Buckets of line indexes by 200 m cell, for the candidates near a point.
    struct Grid: Sendable {
        struct Cell: Hashable, Sendable { let x: Int, y: Int }
        let cell = 200.0
        let b: [Cell: [Int]]

        init(_ lines: [Line]) {
            var b: [Cell: [Int]] = [:]
            for (i, ln) in lines.enumerated() {
                for cx in Int((ln.box.0 / cell).rounded(.down))...Int((ln.box.2 / cell).rounded(.down)) {
                    for cy in Int((ln.box.1 / cell).rounded(.down))...Int((ln.box.3 / cell).rounded(.down)) {
                        b[Cell(x: cx, y: cy), default: []].append(i)
                    }
                }
            }
            self.b = b
        }

        /// Sorted, distinct.
        func near(_ x: Double, _ y: Double, _ r: Double) -> [Int] {
            var out = Set<Int>()
            for cx in Int(((x - r) / cell).rounded(.down))...Int(((x + r) / cell).rounded(.down)) {
                for cy in Int(((y - r) / cell).rounded(.down))...Int(((y + r) / cell).rounded(.down)) {
                    if let l = b[Cell(x: cx, y: cy)] { out.formUnion(l) }
                }
            }
            return out.sorted()
        }
    }

    /// One line (or area) of a run: a segment of runs-and-lifts.json.
    struct Piste: Sendable {
        let key: String
        let named: Bool
        let line: Line
        let area: Bool

        func dist(_ x: Double, _ y: Double) -> Double {
            if area && line.contains(x, y) { return 0 }
            return line.nearest(x, y).d
        }
    }

    struct Station: Sendable {
        let id: String
        /// Position along the cable.
        let u: Double
        let ele: Double
        let name: String?
    }

    /// A lift oriented from its bottom to its top, with its stations along it.
    struct Lift: Sendable {
        let id: String
        let name: String?
        let kind: String?
        let line: Line
        /// The expected cable speed, m/s: from the data (len / dur) or by kind.
        let speed: Double
        let stations: [Station]
        var length: Double { line.length }

        func eleAt(_ u: Double) -> Double {
            let st = stations
            for k in 0..<(st.count - 1) {
                let a = st[k], b = st[k + 1]
                if u <= b.u || k + 1 == st.count - 1 {
                    let f = b.u == a.u ? 0.0 : max(0.0, min(1.0, (u - a.u) / (b.u - a.u)))
                    return a.ele + f * (b.ele - a.ele)
                }
            }
            return st[st.count - 1].ele
        }
    }

    public struct RunInfo: Sendable, Equatable {
        public var color: String?
        /// run, ski-way, beginner-area or ski-route.
        public var kind: String
        public var named: Bool

        public init(color: String?, kind: String = "run", named: Bool) { self.color = color; self.kind = kind; self.named = named }
    }

    public enum NetError: Error, Equatable {
        /// A mid station (lifts[].mid) further from the cable than `lift.mid_max_off_m`: a mistake in the data.
        case midStationOffCable(lift: String)
    }

    /// The resort for the analysis (net.py): projection, runs, lifts and roads, built once from the site's
    /// runs-and-lifts.json and terrain.json.
    public struct Net: Sendable {
        public let proj: Proj
        /// Every run by key, for the moments ("the first red", "all the blues").
        public let runs: [String: RunInfo]
        let pistes: [Piste]
        let lifts: [Lift]
        let grid: Grid
        let roads: [Line]
        let roadGrid: Grid
        /// Per line, sampled every `match.sample_m`: the named runs at that sample (its own, and runs that share it).
        let samples: [[[String]]]

        /// `patch` merges fields into the lift of the same id (a test station only, never in the data).
        public init(runsAndLifts: Data, terrain: Terrain, params P: Params, patch: [String: Any]? = nil) throws {
            guard let rl = try JSONSerialization.jsonObject(with: runsAndLifts) as? [String: Any] else { throw CocoaError(.fileReadCorruptFile) }
            let pj = terrain.raw["proj"] as? [String: Any]
            let proj = Proj(lat0: num(pj?["lat0"]) ?? Geo.lat0, lon0: num(pj?["lon0"]) ?? Geo.lon0)
            self.proj = proj
            func line(_ g: Any?) -> Line? {
                let pts = (g as? [Any] ?? []).compactMap { q -> (x: Double, y: Double)? in
                    guard let q = q as? [Any], q.count >= 2, let la = num(q[0]), let lo = num(q[1]) else { return nil }
                    return proj.xy(la, lo)
                }
                return pts.isEmpty ? nil : Line(pts)
            }
            var pistes: [Piste] = []
            var infos: [String: RunInfo] = [:]
            for run in rl["pistes"] as? [[String: Any]] ?? [] {
                let key = text(run["key"]) ?? ""
                let n = run["named"]
                let named = (n as? Bool) == true || (n as? String) == "True" || (n as? String) == "true"
                infos[key] = RunInfo(color: run["color"] as? String, kind: run["kind"] as? String ?? "run", named: named)
                for s in run["segs"] as? [[String: Any]] ?? [] {
                    guard let ln = line(s["g"]) else { continue }
                    pistes.append(Piste(key: key, named: named, line: ln, area: truthy(s["area"])))
                }
            }
            var lifts: [Lift] = []
            let patchLifts = patch?["lifts"] as? [String: [String: Any]] ?? [:]
            for var l in rl["lifts"] as? [[String: Any]] ?? [] {
                let id = text(l["id"]) ?? ""
                if let extra = patchLifts[id] { for (k, v) in extra { l[k] = v } }
                guard (l["status"] as? String) != "inactive", let g = l["g"] as? [Any], g.count >= 2, var ln = line(g) else { continue }
                var e0 = terrain.elev(ln.xs[0], ln.ys[0]), e1 = terrain.elev(ln.xs[ln.count - 1], ln.ys[ln.count - 1])
                if e1 < e0 { ln = ln.reversed(); swap(&e0, &e1) }
                let kind = l["kind"] as? String
                let speed: Double
                if truthy(l["dur"]), let dur = num(l["dur"]) {
                    let len = truthy(l["len"]) ? (num(l["len"]) ?? ln.length) : ln.length
                    speed = len / (dur * 60)
                } else {
                    speed = P.liftSpeed(kind)
                }
                // stations: bottom, mid (only from the data, lifts[].mid), top; u is the position along the cable
                var st = [Station(id: "bottom", u: 0, ele: e0, name: nil)]
                for (i, m) in (l["mid"] as? [[String: Any]] ?? []).enumerated() {
                    guard let pos = m["pos"] as? [Any], pos.count >= 2, let la = num(pos[0]), let lo = num(pos[1]) else { continue }
                    let (mx, my) = proj.xy(la, lo)
                    let near = ln.nearest(mx, my)
                    if near.d > P["lift", "mid_max_off_m"] { throw NetError.midStationOffCable(lift: id) }
                    let ele = num(m["ele"]) ?? terrain.elev(mx, my)
                    st.append(Station(id: "mid\(i + 1)", u: near.s, ele: ele, name: m["name"] as? String))
                }
                st.append(Station(id: "top", u: ln.length, ele: e1, name: nil))
                st = st.enumerated().sorted { ($0.element.u, $0.offset) < ($1.element.u, $1.offset) }.map(\.element)
                let lift = Lift(id: id, name: l["name"] as? String, kind: kind, line: ln, speed: speed, stations: st)
                if lift.length < P["lift", "min_len_m"] { continue }
                lifts.append(lift)
            }
            self.pistes = pistes
            self.runs = infos
            self.lifts = lifts
            grid = Grid(pistes.map(\.line))
            // the terrain model's roads, in local metres already (as site/js/relief.js reads them)
            var roads: [Line] = []
            for r in (terrain.raw["env"] as? [String: Any])?["roads"] as? [[Any]] ?? [] {
                guard r.count >= 2, let flat = r[1] as? [Any] else { continue }
                var pts: [(x: Double, y: Double)] = []
                var i = 0
                while i + 1 < flat.count { pts.append((num(flat[i]) ?? 0, num(flat[i + 1]) ?? 0)); i += 2 }
                if pts.count >= 2 { roads.append(Line(pts)) }
            }
            self.roads = roads
            roadGrid = Grid(roads)
            samples = Self.labels(pistes, grid, P)
        }

        /// [(line index, distance)] for the lines within r metres, by index.
        func nearPistes(_ x: Double, _ y: Double, _ r: Double) -> [(i: Int, d: Double)] {
            Self.near(pistes, grid, x, y, r)
        }

        static func near(_ pistes: [Piste], _ grid: Grid, _ x: Double, _ y: Double, _ r: Double) -> [(i: Int, d: Double)] {
            var out: [(i: Int, d: Double)] = []
            for i in grid.near(x, y, r) {
                let d = pistes[i].dist(x, y)
                if d <= r { out.append((i, d)) }
            }
            return out
        }

        func nearestRoadDist(_ x: Double, _ y: Double, _ r: Double = 400) -> Double {
            roadGrid.near(x, y, r).map { roads[$0].nearest(x, y).d }.min() ?? 1e9
        }

        func nearestPisteDist(_ x: Double, _ y: Double, _ r: Double = 400) -> Double {
            nearPistes(x, y, r).map(\.d).min() ?? 1e9
        }

        /// Every line sampled along it, with the named runs that share it: only along a stretch of at least
        /// `match.shared_min_m` (a shared stretch, not a crossing).
        static func labels(_ pistes: [Piste], _ grid: Grid, _ P: Params) -> [[[String]]] {
            let step = P["match", "sample_m"], near = P["match", "shared_m"], keep = P["match", "shared_min_m"]
            return pistes.map { q in
                let n = max(1, Int((q.line.length / step).rounded(.down)))
                let pts = (0...n).map { k -> (Double, Double) in let a = q.line.at(q.line.length * Double(k) / Double(n)); return (a.x, a.y) }
                let sets: [Set<String>] = pts.map { p in
                    var s = Set<String>()
                    for (i, _) in Self.near(pistes, grid, p.0, p.1, near) {
                        let o = pistes[i]
                        if o.named && o.key != q.key { s.insert(o.key) }
                    }
                    return s
                }
                var kept = [Set<String>](repeating: [], count: sets.count)
                let span = q.line.length / Double(n)
                for k in sets.reduce(Set<String>(), { $0.union($1) }) {
                    var i = 0
                    while i < sets.count {
                        if sets[i].contains(k) {
                            var j = i
                            while j + 1 < sets.count && sets[j + 1].contains(k) { j += 1 }
                            if Double(j - i) * span >= keep { for t in i...j { kept[t].insert(k) } }
                            i = j + 1
                        } else { i += 1 }
                    }
                }
                return kept.map { k in
                    var s = k
                    if q.named { s.insert(q.key) }
                    return s.sorted(by: pyLess)
                }
            }
        }

        /// The named runs at the point of a line nearest to (x, y).
        func label(_ i: Int, _ x: Double, _ y: Double) -> [String] {
            let q = pistes[i]
            if q.area { return q.named ? [q.key] : [] }
            let s = q.line.nearest(x, y).s
            let smp = samples[i]
            let n = smp.count - 1
            let k = n == 0 ? 0 : q.line.length != 0 ? Int((s / q.line.length * Double(n)).rounded(.toNearestOrEven)) : 0
            return smp[max(0, min(n, k))]
        }
    }
}

extension DayRec {
    /// Python's order of strings: by code point (Swift's `<` is by Unicode collation).
    static func pyLess(_ a: String, _ b: String) -> Bool { a.unicodeScalars.lexicographicallyPrecedes(b.unicodeScalars) }

    /// A JSON number, or a number written as text ("7" minutes in the data).
    static func num(_ v: Any?) -> Double? {
        if let s = v as? String { return Double(s.trimmingCharacters(in: .whitespaces)) }
        if let n = v as? NSNumber { return n.doubleValue }
        return nil
    }

    /// A JSON value as text: ids are numbers in the file.
    static func text(_ v: Any?) -> String? {
        if let s = v as? String { return s }
        if let n = v as? NSNumber {
            let d = n.doubleValue
            return d == d.rounded() && abs(d) < 1e15 ? String(Int64(d)) : String(d)
        }
        return nil
    }

    /// Python's truthiness of a JSON value.
    static func truthy(_ v: Any?) -> Bool {
        switch v {
        case nil, is NSNull: return false
        case let s as String: return !s.isEmpty
        case let n as NSNumber: return n.doubleValue != 0
        case let b as Bool: return b
        case let a as [Any]: return !a.isEmpty
        case let d as [String: Any]: return !d.isEmpty
        default: return true
        }
    }
}
