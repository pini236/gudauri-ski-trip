import Foundation
import GudiData

/// A point of a run's line from its top: projected metres, metres from the top, height, slope in degrees over about 40 m.
public struct RunPoint: Sendable {
    public let x: Double, y: Double, d: Double, h: Double, a: Double
}

/// The run view's numbers (13.3, T2 and T3), computed as the site does (site/js/app.js runProfile, sampleLine,
/// comparable; site/js/relief.js GudRelief.stats) and as Android's data/RunFacts.kt. Checked against numbers the
/// site's own code printed on the real data (RunFactsTests).
///
/// `points`: the run's longest line, top to bottom, every ~10 m. `steepD`, `steepG`, `steepI`, `steepJ`: the steepest
/// 100 m of it (`steepG` 0 when there is none). `top`, `bot`, `drop`, `maxG`: over all of the run's lines. `g0`: the
/// grade of the first 150 m.
public struct RunFacts: Sendable {
    public let points: [RunPoint]
    public let steepD: Double, steepG: Double, steepI: Int, steepJ: Int
    public let top: Int, bot: Int, drop: Int
    public let maxG: Double
    public let g0: Double

    public var length: Double { points.last!.d }

    public typealias Line = [(x: Double, y: Double)]

    /// A line from its higher end.
    public static func topDown(_ t: Terrain, _ l: Line) -> Line {
        guard l.count >= 2, t.elev(l[0].x, l[0].y) < t.elev(l[l.count - 1].x, l[l.count - 1].y) else { return l }
        return l.reversed()
    }

    /// Points every ~`step` metres along a line, with the height and the slope over about 40 m (the site's sampleLine).
    public static func sample(_ t: Terrain, _ l: Line, step: Double) -> [RunPoint] {
        var xs: [Double] = [], ys: [Double] = [], ds: [Double] = []
        var d = 0.0
        for i in l.indices {
            if i == 0 { xs.append(l[0].x); ys.append(l[0].y); ds.append(0); continue }
            let a = l[i - 1], b = l[i]
            let len = hypot(b.x - a.x, b.y - a.y)
            let n = max(1, Int((len / step).rounded()))
            for k in 1...n {
                let f = Double(k) / Double(n)
                xs.append(a.x + (b.x - a.x) * f); ys.append(a.y + (b.y - a.y) * f); ds.append(d + len * f)
            }
            d += len
        }
        let hs = xs.indices.map { t.elev(xs[$0], ys[$0]) }
        return xs.indices.map { i in
            var a = i, b = i
            while a > 0 && ds[i] - ds[a] < 20 { a -= 1 }
            while b < xs.count - 1 && ds[b] - ds[i] < 20 { b += 1 }
            let dd = ds[b] - ds[a]
            let deg = dd > 0 ? atan(abs(hs[b] - hs[a]) / dd) * 180 / .pi : 0
            return RunPoint(x: xs[i], y: ys[i], d: ds[i], h: hs[i], a: deg)
        }
    }

    /// Highest and lowest point, and the steepest ~100 m stretch, over a run's lines (GudRelief.stats).
    public static func stats(_ t: Terrain, _ lines: [Line]) -> (top: Double, bot: Double, maxG: Double) {
        var top = -1e9, bot = 1e9, maxG = 0.0
        for l in lines {
            let n = l.count
            var acc = [Double](repeating: 0, count: n), h = [Double](repeating: 0, count: n)
            for i in 0..<n {
                if i > 0 { acc[i] = acc[i - 1] + hypot(l[i].x - l[i - 1].x, l[i].y - l[i - 1].y) }
                h[i] = t.elev(l[i].x, l[i].y)
                top = max(top, h[i]); bot = min(bot, h[i])
            }
            var j = 0
            for i in 0..<n {
                while j < n - 1 && acc[j] - acc[i] < 100 { j += 1 }
                let dl = acc[j] - acc[i]
                if dl >= 80 { maxG = max(maxG, abs(h[j] - h[i]) / dl) }
            }
        }
        return (top, bot, maxG)
    }

    /// Rounds half up, as JavaScript's Math.round and Kotlin's roundToInt for these positive values.
    static func round(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }

    /// The run's facts, or nil for a run with no line (an area only).
    public static func of(_ t: Terrain, _ p: Piste) -> RunFacts? {
        let lines = p.lines
        guard !lines.isEmpty else { return nil }
        // the longest line, from its top (the first one of equal length, as the site's stable sort)
        var s: [RunPoint] = []
        for l in lines {
            let c = sample(t, topDown(t, l), step: 10)
            if s.isEmpty || c.last!.d > s.last!.d { s = c }
        }
        var bestG = 0.0, bestD = 0.0, bestI = 0, bestJ = 0
        for i in s.indices {
            var j = i
            while j < s.count - 1 && s[j].d - s[i].d < 100 { j += 1 }
            let dd = s[j].d - s[i].d
            if dd >= 60 {
                let g = (s[i].h - s[j].h) / dd
                if g > bestG { bestG = g; bestD = s[i].d; bestI = i; bestJ = j }
            }
        }
        let st = stats(t, lines)
        let first = s.first { $0.d >= 150 } ?? s.last!
        let g0 = (s[0].h - first.h) / (first.d != 0 ? first.d : 1)
        return RunFacts(points: s, steepD: bestD, steepG: bestG, steepI: bestI, steepJ: bestJ,
                        top: round(st.top), bot: round(st.bot), drop: round(st.top - st.bot), maxG: st.maxG, g0: g0)
    }

    /// A grade (rise over run) in whole degrees.
    public static func deg(_ g: Double) -> Int { round(atan(g) * 180 / .pi) }

    /// "About as steep as X, about as long as Y" (T3, the site's comparable()): among the named runs (not ski ways or
    /// beginner areas), the closest by steepest stretch and by length. Nil for a run that is not one of them.
    public static func compare(_ t: Terrain, _ pistes: [Piste], key: String, cache: inout [String: Double]) -> (steep: String, long: String)? {
        let all: [(key: String, g: Double, len: Double)] = pistes.filter { $0.named && $0.kind == "run" && !$0.lines.isEmpty }.map { p in
            let g = cache[p.key] ?? stats(t, p.lines).maxG
            cache[p.key] = g
            return (p.key, g, p.len)
        }
        guard let me = all.first(where: { $0.key == key }), all.count > 2 else { return nil }
        let others = all.filter { $0.key != key }
        // the first of equal distance, as Kotlin's minBy and the site's loop
        func closest(_ by: ((key: String, g: Double, len: Double)) -> Double) -> String {
            var best = others[0]
            for o in others.dropFirst() where by(o) < by(best) { best = o }
            return best.key
        }
        return (closest { abs($0.g - me.g) }, closest { abs($0.len - me.len) })
    }

    /// The named runs in the order the run sign steps through them: by colour, then by name (numbers as numbers).
    public static func order(_ pistes: [Piste]) -> [String] {
        let colors = ["green", "blue", "red", "black"]
        func rank(_ c: String) -> Int { colors.firstIndex(of: c) ?? -1 }
        return pistes.filter(\.named).enumerated().sorted { a, b in
            let ra = rank(a.element.color), rb = rank(b.element.color)
            if ra != rb { return ra < rb }
            let c = natural(a.element.key, b.element.key)
            return c != 0 ? c < 0 : a.offset < b.offset
        }.map(\.element.key)
    }

    /// "Kudebi 2" before "Kudebi 10": letters without case, runs of digits by their value (localeCompare, numeric).
    public static func natural(_ a: String, _ b: String) -> Int {
        let a = Array(a), b = Array(b)
        var i = 0, j = 0
        while i < a.count && j < b.count {
            if a[i].isNumber && b[j].isNumber {
                var x = i; while x < a.count && a[x].isNumber { x += 1 }
                var y = j; while y < b.count && b[y].isNumber { y += 1 }
                let da = String(a[i..<x]).drop { $0 == "0" }, db = String(b[j..<y]).drop { $0 == "0" }
                if da.count != db.count { return da.count < db.count ? -1 : 1 }
                if da != db { return da < db ? -1 : 1 }
                i = x; j = y
            } else {
                let ca = a[i].lowercased(), cb = b[j].lowercased()
                if ca != cb { return ca < cb ? -1 : 1 }
                i += 1; j += 1
            }
        }
        return (a.count - i) < (b.count - j) ? -1 : ((a.count - i) == (b.count - j) ? 0 : 1)
    }
}
