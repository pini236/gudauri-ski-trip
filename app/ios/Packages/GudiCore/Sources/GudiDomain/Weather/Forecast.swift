import Foundation

/// Weather by altitude (round 19, decision 58; docs/ARCHITECTURE.md m-6), as Android's weather/Weather.kt: the server
/// fetches Open-Meteo once an hour for three points and the ridge, and every client reads its one answer at
/// /api/weather. The client does not compute the risk; it picks the hour, keeps the last answer, and says how old it
/// is, by the rules in tools/fixtures/weather-m6.json. Times without a zone are Gudauri time (UTC+4), as text
/// ("2027-01-12T11:00", "2027-01-12").
public struct Forecast: Sendable {
    public struct Point: Sendable {
        public let id: String
        public let elevation: Int
        public let lat: Double, lon: Double
        let snow24: Double?
        let hours: [String]
        let h: [String: [Double?]]
        let days: [String]
        let d: [String: [Double?]]
    }

    /// What a point (or an altitude between them) has at one hour; a value the answer leaves null is nil ("no data").
    public struct Now: Equatable, Sendable {
        public let temp: Int?, wind: Int?, gust: Int?, dir: Int?, snow24: Int?
        public init(temp: Int?, wind: Int?, gust: Int?, dir: Int?, snow24: Int?) {
            self.temp = temp; self.wind = wind; self.gust = gust; self.dir = dir; self.snow24 = snow24
        }
    }

    /// One day of a point (the days board before the trip).
    public struct DayValues: Equatable, Sendable {
        public let date: String
        public let tmin: Int?, tmax: Int?, snow: Int?, wind: Int?, gust: Int?, dir: Int?
    }

    public enum State: String, Sendable { case fresh, stale, none }

    public static let freshMs: Int64 = 12 * 3600_000
    public static let staleMs: Int64 = 48 * 3600_000
    public static let levels = ["low", "medium", "high"]
    /// The three points, top down, as the lists show them.
    public static let pointIds = ["sadzele", "goodaura", "village"]
    /// The lifts whose closing in the wind the boards estimate (FEATURES section c).
    public static let riskLifts = ["Sadzele", "Kudebi"]

    /// Milliseconds since 1970.
    public let updated: Int64
    public let points: [Point]
    let ridgeDates: [String]
    let risk: [String: [String]]

    public func state(now: Int64) -> State {
        let ms = now - updated
        return ms <= Self.freshMs ? .fresh : ms <= Self.staleMs ? .stale : .none
    }

    public func point(_ id: String) -> Point? { points.first { $0.id == id } }

    /// The hour that has begun ("2027-01-12T11:25" reads "2027-01-12T11:00").
    static func hourKey(_ at: String) -> String { String(at.prefix(13)) }

    private func index(_ p: Point, _ at: String) -> Int? {
        let k = Self.hourKey(at)
        return p.hours.firstIndex { $0.hasPrefix(k) }
    }

    /// Half up, as JavaScript's Math.round (the site's rounding; -2.5 is -2).
    public static func round(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }
    static func r(_ x: Double?) -> Int? { x.map(round) }

    /// The values of the hour that has begun; nil when that hour is not in the answer.
    public func now(_ id: String, at: String) -> Now? {
        guard let p = point(id), let i = index(p, at) else { return nil }
        return Now(temp: Self.r(p.h["temp"]![i]), wind: Self.r(p.h["wind"]![i]), gust: Self.r(p.h["gust"]![i]),
                   dir: p.h["dir"]![i].map { Int($0) }, snow24: Self.r(p.snow24))
    }

    /// At an altitude (a run's top or bottom): linear between the points above and below, the nearest one outside them.
    public func at(alt: Double, at: String) -> Now? {
        let ps: [(Int, [Double?])] = points.sorted { $0.elevation < $1.elevation }.compactMap { p in
            index(p, at).map { i in (p.elevation, [p.h["temp"]![i], p.h["wind"]![i], p.h["gust"]![i], p.h["dir"]![i], p.snow24]) }
        }
        guard ps.count == points.count, let first = ps.first, let last = ps.last else { return nil }
        func rounded(_ v: [Double?]) -> Now { Now(temp: Self.r(v[0]), wind: Self.r(v[1]), gust: Self.r(v[2]), dir: v[3].map { Int($0) }, snow24: Self.r(v[4])) }
        if alt <= Double(first.0) { return rounded(first.1) }
        if alt >= Double(last.0) { return rounded(last.1) }
        for i in 0..<(ps.count - 1) {
            let (ea, a) = ps[i], (eb, b) = ps[i + 1]
            guard alt >= Double(ea), alt <= Double(eb) else { continue }
            let t = (alt - Double(ea)) / Double(eb - ea)
            // a null on either side is null
            func mix(_ k: Int) -> Int? { guard let x = a[k], let y = b[k] else { return nil }; return Self.round(x + t * (y - x)) }
            return Now(temp: mix(0), wind: mix(1), gust: mix(2), dir: (t < 0.5 ? a[3] : b[3]).map { Int($0) }, snow24: mix(4))
        }
        return nil
    }

    public func day(_ id: String, date: String) -> DayValues? {
        guard let p = point(id), let i = p.days.firstIndex(of: date) else { return nil }
        func v(_ k: String) -> Int? { Self.r(p.d[k]![i]) }
        return DayValues(date: date, tmin: v("tmin"), tmax: v("tmax"), snow: v("snow"), wind: v("wind"), gust: v("gust"), dir: p.d["dir"]![i].map { Int($0) })
    }

    /// The last day the answer reaches (the days board: "opens on" for the days after it).
    public var lastDay: String? { points.first?.days.last }
    public var dayCount: Int { points.first?.days.count ?? 0 }

    /// Whether the server estimates this lift at all (a lift it leaves out has no risk row).
    public func hasRisk(_ lift: String) -> Bool { risk[lift] != nil }

    /// The server's daily estimate for a lift ("low", "medium", "high"), or nil.
    public func risk(_ lift: String, date: String) -> String? {
        guard let i = ridgeDates.firstIndex(of: date), let row = risk[lift], i < row.count, Self.levels.contains(row[i]) else { return nil }
        return row[i]
    }

    public static func parse(_ data: Data) -> Forecast? {
        guard let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              (o["schema"] as? NSNumber)?.intValue == 1, let up = o["updated"] as? String,
              let updated = isoMillis(up), let pts = o["points"] as? [[String: Any]], !pts.isEmpty else { return nil }
        func nums(_ a: Any?) -> [Double?]? { (a as? [Any]).map { $0.map { ($0 as? NSNumber)?.doubleValue } } }
        var points: [Point] = []
        for p in pts {
            guard let id = p["id"] as? String, let el = (p["elevation"] as? NSNumber)?.intValue,
                  let h = p["hourly"] as? [String: Any], let d = p["daily"] as? [String: Any],
                  let hours = h["time"] as? [String], let days = d["date"] as? [String] else { return nil }
            var hv: [String: [Double?]] = [:], dv: [String: [Double?]] = [:]
            for k in ["temp", "wind", "gust", "dir"] { guard let a = nums(h[k]), a.count == hours.count else { return nil }; hv[k] = a }
            for k in ["tmin", "tmax", "snow", "wind", "gust", "dir"] { guard let a = nums(d[k]), a.count == days.count else { return nil }; dv[k] = a }
            points.append(Point(id: id, elevation: el, lat: (p["lat"] as? NSNumber)?.doubleValue ?? 0, lon: (p["lon"] as? NSNumber)?.doubleValue ?? 0,
                                snow24: (p["snow24"] as? NSNumber)?.doubleValue, hours: hours, h: hv, days: days, d: dv))
        }
        let ridge = (o["ridge"] as? [String: Any])?["daily"] as? [String: Any]
        let dates = ridge?["date"] as? [String] ?? []
        var risk: [String: [String]] = [:]
        for (k, v) in (ridge?["risk"] as? [String: Any]) ?? [:] { risk[k] = (v as? [Any])?.map { $0 as? String ?? "" } }
        return Forecast(updated: updated, points: points, ridgeDates: dates, risk: risk)
    }

    static func isoMillis(_ s: String) -> Int64? {
        for frac in [false, true] {
            let f = ISO8601DateFormatter()
            f.formatOptions = frac ? [.withInternetDateTime, .withFractionalSeconds] : [.withInternetDateTime]
            if let d = f.date(from: s) { return Int64((d.timeIntervalSince1970 * 1000).rounded()) }
        }
        return nil
    }

    /// Gudauri's local time as the answer writes it (UTC+4): "2027-01-12T11:25" and "2027-01-12".
    public static func local(_ ms: Int64) -> (time: String, date: String) {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(secondsFromGMT: 4 * 3600)!
        let z = c.dateComponents([.year, .month, .day, .hour, .minute], from: Date(timeIntervalSince1970: Double(ms) / 1000))
        let date = String(format: "%04d-%02d-%02d", z.year!, z.month!, z.day!)
        return (date + String(format: "T%02d:%02d", z.hour!, z.minute!), date)
    }

    /// Gudauri's time "2027-01-12T11:25" as milliseconds since 1970.
    public static func millis(local s: String) -> Int64? {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(secondsFromGMT: 4 * 3600)
        f.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return f.date(from: s).map { Int64($0.timeIntervalSince1970 * 1000) }
    }
}
