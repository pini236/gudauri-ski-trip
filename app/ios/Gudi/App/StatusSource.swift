import Foundation
import GudiDomain

/// Where the lift report comes from (Android's StatusSource): the site's /api/status, read again every five minutes
/// while the app is open in season, with the last good one kept on the phone, so a restart on the mountain without
/// reception still shows it while it is fresh. Out of season the site is asked once a day at most (R-18).
enum StatusSource {
    static let url = URL(string: "https://gudauri-ski-trip.vercel.app/api/status")!
    static let dayMs: Int64 = 24 * 3600_000
    private static var d: UserDefaults { .standard }

    static func cached() -> Report? { d.data(forKey: "lstat.report").flatMap(Report.parse) }

    /// Ask the site; on any failure the cached report (checked for freshness by LiftStatus like any other).
    static func load(inSeason: Bool, now: Int64) async -> Report? {
        if !inSeason, let asked = d.object(forKey: "lstat.asked") as? Int64, (0..<dayMs).contains(now - asked) { return cached() }
        d.set(now, forKey: "lstat.asked")
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let r = Report.parse(data) else { return cached() }
        d.set(data, forKey: "lstat.report")
        return r
    }

    /// The state the board showed last time ("opened since you checked").
    static func before() -> [String: Bool?]? {
        guard let o = d.dictionary(forKey: "lstat.seen") else { return nil }
        var m: [String: Bool?] = [:]
        for (k, v) in o { m[k] = .some(v as? Bool) }
        return m
    }

    static func seen(_ state: [String: Bool?]) {
        // a lift the report said nothing about is kept as "null", read back as unknown
        var o: [String: Any] = [:]
        for (k, v) in state { o[k] = v.map { $0 as Any } ?? "null" }
        d.set(o, forKey: "lstat.seen")
    }

    /// The screenshots: a report of their own (a launch argument), never the site's.
    static func qa(_ kind: String, now: Int64) -> Report? {
        let iso = ISO8601DateFormatter()
        let fresh = iso.string(from: Date(timeIntervalSince1970: Double(now - 6 * 60_000) / 1000))
        let old = iso.string(from: Date(timeIntervalSince1970: Double(now - 3 * 3600_000) / 1000))
        let lifts = #"{"Goodaura":{"open":true},"Kudebi":{"open":true},"Sadzele":{"open":false,"reason":"wind"},"Shino":{"open":true},"Soliko":{"open":true},"Kikilo":{"open":true},"Zuma":{"open":true},"New Goodaura":{"open":true},"Pirveli":{"open":true},"Khada":{"open":true},"Firni":{"open":true},"Snow Park":{"open":false,"reason":"maintenance"}}"#
        switch kind {
        case "fresh": return Report.parse(Data(#"{"updated":"\#(fresh)","lifts":\#(lifts),"pistes":{}}"#.utf8))
        case "old": return Report.parse(Data(#"{"updated":"\#(old)","lifts":\#(lifts),"pistes":{}}"#.utf8))
        default: return nil
        }
    }
}
