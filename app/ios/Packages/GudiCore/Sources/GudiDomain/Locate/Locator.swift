import Foundation
import GudiData

/// "Where am I" (round 19, decision 58; docs/ARCHITECTURE.md m-7 section 6), as Android's map/Locate.kt: which run or
/// lift a reading is on, by the one rule the site uses too, checked against tools/fixtures/location-m7.json.
///
/// Nothing leaves this type and nothing is written anywhere: it keeps only the run of the reading before and the last
/// three positions along a lift, in memory, and `reset` drops them when the button goes off.
public struct Locator: Sendable {
    public enum Fix: Equatable, Sendable {
        /// Outside the terrain model: "not in Gudauri right now", no dot.
        case outside
        /// Only an approximate location: a big circle, no snapping.
        case approx
        /// Accuracy over 50 m, or over 30 m when not on a lift: no snapping.
        case low
        case onLift(String)
        case onRun(String)
        /// Accurate, but not within 30 m of a run.
        case free
    }

    public static let low = 50.0, snap = 30.0, stick = 10.0, liftNear = 25.0
    typealias Line = [(x: Double, y: Double)]

    private struct Cable: @unchecked Sendable { let name: String; let up: Bool; let len: Double; let line: Line }
    private struct Run: @unchecked Sendable { let key: String; let lines: [Line] }

    private let runs: [Run]
    private let cables: [Cable]
    private let bounds: (x0: Double, x1: Double, y0: Double, y1: Double)
    private var prev: String?
    private var trackLift: String?
    private var track: [Double] = []

    public init(runs: RunsAndLifts, terrain: Terrain) {
        self.runs = runs.pistes.filter { $0.named && !$0.lines.isEmpty }.map { Run(key: $0.key, lines: $0.lines) }
        cables = runs.lifts.filter { !$0.name.trimmingCharacters(in: .whitespaces).isEmpty && $0.status != "inactive" && $0.g.count >= 2 }.map { l in
            let p = l.line
            return Cable(name: l.name, up: terrain.elev(p[0].x, p[0].y) < terrain.elev(p[p.count - 1].x, p[p.count - 1].y), len: Self.length(p), line: p)
        }
        bounds = (terrain.x0, terrain.x1, terrain.y0, terrain.y1)
    }

    public mutating func reset() { prev = nil; forget() }
    private mutating func forget() { trackLift = nil; track = [] }

    public func inside(_ x: Double, _ y: Double) -> Bool { x >= bounds.x0 && x <= bounds.x1 && y >= bounds.y0 && y <= bounds.y1 }

    /// One reading, in the site's projection (metres), with the radius the phone reports.
    public mutating func feed(x: Double, y: Double, accuracy: Double, approximate: Bool) -> Fix {
        guard inside(x, y) else { reset(); return .outside }
        if approximate { reset(); return .approx }
        if accuracy > Self.low { reset(); return .low }
        // a lift: within 25 m of its cable, and moving up along it in three readings in a row
        var best: Cable?, bestD = Double.infinity, bestAt = 0.0
        for c in cables {
            let (d, along) = Self.near(x, y, c.line)
            if d <= Self.liftNear && d < bestD { best = c; bestD = d; bestAt = c.up ? along : c.len - along }
        }
        if let best {
            if trackLift != best.name { trackLift = best.name; track = [] }
            if track.count == 3 { track.removeFirst() }
            track.append(bestAt)
            if track.count == 3 && track[1] > track[0] && track[2] > track[1] { prev = nil; return .onLift(best.name) }
        } else { forget() }
        if accuracy > Self.snap { prev = nil; return .low }
        // a run: the nearest one within 30 m; the one before stays while it is within 30 m and less than 10 m farther
        var run: String?, runD = Double.infinity
        for r in runs { let d = distance(x, y, r); if d < runD { run = r.key; runD = d } }
        guard var chosen = run, runD <= Self.snap else { prev = nil; return .free }
        if let before = prev, before != chosen, let b = runs.first(where: { $0.key == before }) {
            let d = distance(x, y, b)
            if d <= Self.snap && d - runD < Self.stick { chosen = before }
        }
        prev = chosen
        return .onRun(chosen)
    }

    private func distance(_ x: Double, _ y: Double, _ r: Run) -> Double { r.lines.map { Self.near(x, y, $0).0 }.min() ?? .infinity }

    /// The distance from (x, y) to a polyline, and where along it the nearest point is, from its first point.
    static func near(_ x: Double, _ y: Double, _ p: Line) -> (Double, Double) {
        var best = Double.infinity, at = 0.0, acc = 0.0
        for i in 1..<max(p.count, 1) {
            let a = p[i - 1], b = p[i], dx = b.x - a.x, dy = b.y - a.y, l2 = dx * dx + dy * dy
            let t = l2 == 0 ? 0 : min(max(((x - a.x) * dx + (y - a.y) * dy) / l2, 0), 1)
            let d = hypot(x - (a.x + dx * t), y - (a.y + dy * t)), l = l2.squareRoot()
            if d < best { best = d; at = acc + t * l }
            acc += l
        }
        return (best, at)
    }

    static func length(_ p: Line) -> Double {
        var s = 0.0
        for i in 1..<max(p.count, 1) { s += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y) }
        return s
    }
}
