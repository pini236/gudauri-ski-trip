import Foundation
import GudiData

/// One report from the site's /api/status (round 3, S1 to S3), the format the site and Android read (LSTAT in
/// site/js/app.js): {updated:"ISO time", lifts:{"<lift name>":{open, reason?}}, pistes:{"<run key>":{open}}}.
public struct Report: Sendable {
    public struct LiftState: Sendable, Equatable { public let open: Bool; public let reason: String? }
    /// Milliseconds since 1970.
    public let updated: Int64
    public let lifts: [String: LiftState]
    public let pistes: [String: Bool]

    /// A report needs a time and lifts, as on the site; anything else is "no report".
    public static func parse(_ data: Data) -> Report? {
        guard let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let t = o["updated"] as? String, !t.isEmpty, let ls = o["lifts"] as? [String: Any],
              let when = isoMillis(t) else { return nil }
        var lifts: [String: LiftState] = [:]
        for (k, v) in ls {
            guard let l = v as? [String: Any] else { continue }
            let r = (l["reason"] as? String).flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
            lifts[k] = LiftState(open: (l["open"] as? Bool) ?? false, reason: r)
        }
        var pistes: [String: Bool] = [:]
        for (k, v) in (o["pistes"] as? [String: Any]) ?? [:] {
            if let p = v as? [String: Any] { pistes[k] = (p["open"] as? Bool) ?? false }
        }
        return Report(updated: when, lifts: lifts, pistes: pistes)
    }

    static func isoMillis(_ s: String) -> Int64? {
        for withFraction in [false, true] {
            let f = ISO8601DateFormatter()
            f.formatOptions = withFraction ? [.withInternetDateTime, .withFractionalSeconds] : [.withInternetDateTime]
            if let d = f.date(from: s) { return Int64((d.timeIntervalSince1970 * 1000).rounded()) }
        }
        return nil
    }
}

/// The state of the lifts now. No report, or one older than 30 minutes: "no current information", and the map stays
/// as it is. We never guess (the site's rule). The same as Android's `status/LiftStatus.kt`.
public struct LiftStatus: Sendable {
    public static let staleMs: Int64 = 30 * 60_000
    public static let everyMs: Int64 = 5 * 60_000

    public let names: [String]
    public let report: Report?
    public let now: Int64

    public init(names: [String], report: Report?, now: Int64) { self.names = names; self.report = report; self.now = now }

    public var fresh: Bool { report.map { now - $0.updated < Self.staleMs } ?? false }

    /// true or false from a fresh report, nil when there is none or it says nothing about this lift.
    public func isOpen(_ name: String?) -> Bool? {
        guard fresh, let name else { return nil }
        return report?.lifts[name]?.open
    }

    public func reason(_ name: String) -> String? { fresh ? report?.lifts[name]?.reason : nil }

    /// Open for me: the run itself, and a lift up to it (the site's runOpen).
    public func runOpen(_ p: Piste) -> Bool? {
        guard fresh, let r = report else { return nil }
        let own = r.pistes[p.key]
        if own == false { return false }
        let up = p.fromLifts.compactMap { r.lifts[$0] }
        return up.isEmpty ? own : up.contains { $0.open }
    }

    public var open: Int { names.filter { isOpen($0) == true }.count }

    /// The state at this report, kept to tell "opened since you checked" next time.
    public func snapshot() -> [String: Bool?] {
        var m: [String: Bool?] = [:]
        for n in names { m[n] = isOpen(n) }
        return m
    }

    /// The lifts that changed since `before`, with their state now; a lift the report says nothing about is left out.
    public func changes(_ before: [String: Bool?]?) -> [(String, Bool)] {
        guard let before, fresh else { return [] }
        return names.compactMap { n in
            guard let o = isOpen(n), let was = before[n], was != o else { return nil }
            return (n, o)
        }
    }

    /// Minutes since the report, for "updated 6 min ago" (rounded like Math.round).
    public var minutesAgo: Int {
        guard let r = report else { return 0 }
        return Int((Double(now - r.updated) / 60_000 + 0.5).rounded(.down))
    }

    /// The lifts on the board: the main side's named lifts that run (the site's names).
    public static func names(_ mainLifts: [Lift]) -> [String] {
        mainLifts.filter { !$0.name.trimmingCharacters(in: .whitespaces).isEmpty && $0.status != "inactive" }.map(\.name)
    }

    /// In season (December to April) "no current information"; out of it, the mountain sleeps.
    public static func inSeason(month: Int) -> Bool { [12, 1, 2, 3, 4].contains(month) }
}
