import Foundation
import GudiData

/// Run names beside the line, curving with it (round 21): site/js/labelfit.js and Android's map/LabelFit.kt. Points are
/// projected metres, y down.
public enum LabelFit {
    public typealias P = (x: Double, y: Double)

    public struct Fit {
        /// The baseline the text follows, left to right.
        public let pts: [P]
        public let side: Character
        public let win: LabelWindow
    }

    public static func length(_ l: [P]) -> Double {
        var d = 0.0
        for i in 1..<max(l.count, 1) { d += hypot(l[i].x - l[i - 1].x, l[i].y - l[i - 1].y) }
        return d
    }

    /// The part of a polyline between distances a and b.
    public static func cut(_ l: [P], _ a: Double, _ b: Double) -> [P] {
        var o: [P] = [], acc = 0.0
        for i in 1..<max(l.count, 1) {
            let p = l[i - 1], q = l[i], ln = hypot(q.x - p.x, q.y - p.y)
            if acc + ln >= a, acc <= b, ln > 0 {
                let u0 = max(0, (a - acc) / ln), u1 = min(1, (b - acc) / ln)
                if o.isEmpty { o.append((p.x + (q.x - p.x) * u0, p.y + (q.y - p.y) * u0)) }
                o.append((p.x + (q.x - p.x) * u1, p.y + (q.y - p.y) * u1))
            }
            acc += ln
        }
        return o
    }

    /// Every point k metres to the left of the direction of travel (screen, y down).
    public static func shift(_ l: [P], _ k: Double) -> [P] {
        l.enumerated().map { i, p in
            let a = l[max(i - 1, 0)], b = l[min(i + 1, l.count - 1)]
            var tx = b.x - a.x, ty = b.y - a.y
            let t = hypot(tx, ty); let n = t == 0 ? 1 : t
            tx /= n; ty /= n
            return (p.x + ty * k, p.y - tx * k)
        }
    }

    /// The first window with room for a name `wM` metres long and `hM` high, `off` from the line's centre; nil when none has.
    public static func fit(_ windows: [LabelWindow], segs: [[P]], wM: Double, hM: Double, off: Double) -> Fit? {
        let needL = off + hM * 0.8 + hM * 0.2, needR = off + hM * 1.05 + hM * 0.2
        for w in windows {
            guard w.s >= 0, w.s < segs.count, w.t0 < w.t1 else { continue }
            let l = segs[w.s], tot = length(l), a = tot * w.t0 / 1000, b = tot * w.t1 / 1000
            if b - a < wM * 1.1 { continue }
            let mid = (a + b) / 2
            var sub = cut(l, mid - wM * 0.55, mid + wM * 0.55)
            if sub.count < 2 { continue }
            var left = w.cL, right = w.cR
            if sub[0].x > sub[sub.count - 1].x { sub.reverse(); left = w.cR; right = w.cL }
            if left >= needL { return Fit(pts: shift(sub, off), side: "L", win: w) }
            if right >= needR { return Fit(pts: shift(sub, -(off + hM * 0.8)), side: "R", win: w) }
        }
        return nil
    }

    /// One name for a stretch two runs share: "Tatra 1 · 2" when both are of one family, else the first run's own.
    public static func sharedName(_ a: String, _ b: String) -> String {
        func fam(_ k: String) -> (String, Int)? {
            guard let sp = k.lastIndex(of: " ") else { return nil }
            let num = k[k.index(after: sp)...], pre = k[..<sp].trimmingCharacters(in: .whitespaces)
            guard !pre.isEmpty, !num.isEmpty, num.allSatisfy({ $0.isASCII && $0.isNumber }), let n = Int(num) else { return nil }
            return (pre, n)
        }
        if let x = fam(a), let y = fam(b), x.0 == y.0 { return "\(x.0) \(min(x.1, y.1)) · \(max(x.1, y.1))" }
        return a
    }
}

/// The open end of a partial run (round 21): the last stretch of the line fades to nothing. site/js/app.js applyOpenEnds
/// and Android's map/OpenEnds.kt. Only from conf medium up; the elevation model says which end is the top.
public enum OpenEnds {
    public static let fadeM = 130.0

    public struct Fade: Equatable {
        public let line: Int
        public let a: LabelFit.P, b: LabelFit.P
        public static func == (l: Fade, r: Fade) -> Bool { l.line == r.line && l.a == r.a && l.b == r.b }
    }

    public static func fades(_ p: Piste, elev: (Double, Double) -> Double) -> [Fade] {
        var out: [Fade] = [], done = Set<String>()
        let lines = p.lines
        for o in p.openEnds where (o.conf == "medium" || o.conf == "high") && (o.at == "start" || o.at == "end") {
            var best = -1, bestFirst = true, bestH = 0.0
            for (i, l) in lines.enumerated() where l.count >= 2 {
                for first in [true, false] {
                    let q = first ? l[0] : l[l.count - 1], h = elev(q.x, q.y)
                    if best < 0 || (o.at == "start" ? h > bestH : h < bestH) { best = i; bestFirst = first; bestH = h }
                }
            }
            guard best >= 0, done.insert("\(o.at)\(best)").inserted else { continue }
            let l = bestFirst ? lines[best] : Array(lines[best].reversed())
            var acc = 0.0, i = 1
            while i < l.count, acc + hypot(l[i].x - l[i - 1].x, l[i].y - l[i - 1].y) < fadeM {
                acc += hypot(l[i].x - l[i - 1].x, l[i].y - l[i - 1].y); i += 1
            }
            if i >= l.count { i = l.count - 1 }
            if hypot(l[i].x - l[0].x, l[i].y - l[0].y) < 1 { continue }
            out.append(Fade(line: best, a: l[0], b: l[i]))
        }
        return out
    }
}

/// Small arrows down the chosen run (round 21): the first at 90 m, then every 150 m, pointing the way it runs downhill.
public enum RunArrows {
    public static let firstM = 90.0, everyM = 150.0

    /// `segs`: the pieces of the chosen run's lines, top first, one line after the other. Returns x, y and the angle in radians.
    public static func along(_ segs: [(a: LabelFit.P, b: LabelFit.P)]) -> [(x: Double, y: Double, angle: Double)] {
        var out: [(x: Double, y: Double, angle: Double)] = []
        var acc = 0.0, next = firstM
        var prev: LabelFit.P?
        for s in segs {
            if let p = prev, hypot(s.a.x - p.x, s.a.y - p.y) > 1 { acc = 0; next = firstM } else if prev == nil { acc = 0; next = firstM }
            if acc >= next { out.append((s.a.x, s.a.y, atan2(s.b.y - s.a.y, s.b.x - s.a.x))); next += everyM }
            acc += hypot(s.b.x - s.a.x, s.b.y - s.a.y)
            prev = s.b
        }
        return out
    }

    /// Every nth arrow stays: about 60 px apart on screen.
    public static func step(metresPerPx: Double) -> Int { max(1, Int((60 * metresPerPx / everyM).rounded(.up))) }
}
