import Foundation
import GudiDomain

/// Where the forecast comes from (Android's WeatherSource): the site's /api/weather (which leads to the server), at
/// most every ten minutes while the app is open, and the last good answer kept in a file, so the mountain without
/// reception still shows it with how old it is (m-6 section 4). Nothing about the person goes with the request.
enum WeatherSource {
    static let url = URL(string: "https://gudauri-ski-trip.vercel.app/api/weather")!
    static let everyMs: Int64 = 10 * 60_000
    private static var file: URL { FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("weather.json") }

    static func cached() -> Forecast? { (try? Data(contentsOf: file)).flatMap(Forecast.parse) }

    static func load(now: Int64) async -> Forecast? {
        let d = UserDefaults.standard
        if let asked = d.object(forKey: "weather.asked") as? Int64, (0..<everyMs).contains(now - asked) { return cached() }
        d.set(now, forKey: "weather.asked")
        let old = cached()
        guard let (data, resp) = try? await URLSession.shared.data(from: url), (resp as? HTTPURLResponse)?.statusCode == 200,
              let f = Forecast.parse(data) else { return old }
        // never a forecast older than the one already here
        if let old, f.updated < old.updated { return old }
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: file, options: .atomic)
        return f
    }

    /// The screenshots' answer (a launch flag), with the canvas's sample numbers (design/round19/proto.js SAMPLE), as
    /// Android's Forecast.sample: the village −4°, upper Goodaura −9°, Sadzele −13°; Sadzele's closing high, Kudebi's medium.
    static func sample(now: Int64) -> Forecast? {
        let start = String(Forecast.local(now).time.prefix(13)) + ":00"
        let startMs = Forecast.millis(local: start)!
        func hour(_ i: Int) -> String { Forecast.local(startMs + Int64(i) * 3600_000).time }
        let day0 = Forecast.millis(local: Forecast.local(now).date + "T12:00")!
        func day(_ i: Int) -> String { Forecast.local(day0 + Int64(i) * 86_400_000).date }
        let base: [(String, Double, Double, Double, Double, Double, Double, Double, Double)] = [
            ("village", 2170, -4, 8, 15, 290, 5, 42.471394, 44.49246),
            ("goodaura", 2710, -9, 24, 40, 300, 9, 42.492379, 44.494273),
            ("sadzele", 3240, -13, 48, 70, 300, 12, 42.508985, 44.503209),
        ]
        let dw = [15, 32, 10], dg = [22, 55, 16], ds = [4, 12, 0], dt = [-7, -10, -6], dd = [300, 300, 240]
        var pts: [[String: Any]] = []
        for b in base {
            let shift = Int(b.2 + 9)
            pts.append(["id": b.0, "lat": b.7, "lon": b.8, "elevation": Int(b.1), "snow24": b.6,
                        "hourly": ["time": (0..<72).map(hour), "temp": Array(repeating: b.2, count: 72), "wind": Array(repeating: b.3, count: 72),
                                   "gust": Array(repeating: b.4, count: 72), "dir": Array(repeating: b.5, count: 72)],
                        "daily": ["date": (0..<15).map(day), "tmin": (0..<15).map { dt[$0 % 3] - 4 + shift }, "tmax": (0..<15).map { dt[$0 % 3] + shift },
                                  "snow": (0..<15).map { ds[$0 % 3] }, "wind": (0..<15).map { dw[$0 % 3] }, "gust": (0..<15).map { dg[$0 % 3] },
                                  "dir": (0..<15).map { dd[$0 % 3] }]])
        }
        let iso = ISO8601DateFormatter().string(from: Date(timeIntervalSince1970: Double(now) / 1000))
        let o: [String: Any] = ["schema": 1, "updated": iso, "model": "sample", "points": pts,
                                "ridge": ["daily": ["date": (0..<15).map(day), "risk": ["Sadzele": Array(repeating: "high", count: 15), "Kudebi": Array(repeating: "medium", count: 15)]]]]
        return (try? JSONSerialization.data(withJSONObject: o)).flatMap(Forecast.parse)
    }
}

/// The forecast and the time, for every screen that shows the weather.
struct WeatherNow {
    let forecast: Forecast?
    let now: Int64
    var local: String { Forecast.local(now).time }
    var today: String { Forecast.local(now).date }
    var state: Forecast.State { forecast?.state(now: now) ?? .none }
    /// The forecast when it may be shown (fresh or old), else nil: "no data", no guessing.
    var usable: Forecast? { state == .none ? nil : forecast }
}
