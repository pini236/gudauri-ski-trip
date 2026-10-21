import SwiftUI
import GudiData
import GudiDomain

/// Where the app can go, from home's signs and from links (Android's nav/Nav.kt).
enum Route: Hashable {
    case map(run: String? = nil)
    case meet(station: String? = nil)
    case games
    case game(String)
    case group
    case about
    case trip

    /// "map", "map:Tatra 2", "meet", "games", "game:merge", "group", "about", "trip" (the screenshots' launch argument).
    init?(qa: String) {
        let parts = qa.split(separator: ":", maxSplits: 1).map(String.init)
        // "_" for a space, as the screenshot list splits on spaces ("map:Tatra_2")
        let arg = parts.count > 1 ? parts[1].replacingOccurrences(of: "_", with: " ") : nil
        switch parts.first {
        case "map": self = .map(run: arg)
        case "meet": self = .meet(station: arg)
        case "games": self = .games
        case "game": guard let arg else { return nil }; self = .game(arg)
        case "group": self = .group
        case "about": self = .about
        case "trip": self = .trip
        default: return nil
        }
    }
}

/// What the whole app shares (the site's data, the user's trip, the lift status), kept by the root and read by screens
/// through the environment (`@Environment(AppModel.self)`). Data comes only from the site (decision 25); the trip only on the phone (decision 27).
@MainActor
@Observable
final class AppModel {
    let runs: RunsAndLifts?
    /// The terrain model (site/data/terrain.json), parsed by the map when it first opens.
    let terrainURL: URL?
    var status: LiftStatus
    var path: [Route] = []
    /// The lift board's sheet over the map (S2, S3), opened from home's board or the map's bar.
    var statusSheet = false
    /// The last weather answer (round 19), kept on the phone.
    var forecast: Forecast?
    /// The lift the trail map should frame and open when it next opens ("see on the trail map" from the meeting point);
    /// the map clears it once read.
    var mapLift: String?

    /// The user's own trip, as JSON in Android's format, in UserDefaults.
    var trip: Trip? {
        didSet { UserDefaults.standard.set(trip?.json(), forKey: "trip") }
    }

    init() {
        let data = { (name: String) in Bundle.main.url(forResource: name, withExtension: "json", subdirectory: "Data") }
        runs = data("runs-and-lifts").flatMap { try? Data(contentsOf: $0) }.flatMap { try? RunsAndLifts.decode($0) }
        terrainURL = data("terrain")
        let names = runs.map { LiftStatus.names($0.mainLifts) } ?? []
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        // the screenshots' screen and switches, one launch argument: "map:Tatra_2+status=fresh+sheet=status"
        let qa = (UserDefaults.standard.string(forKey: "qaRoute") ?? "").split(separator: "+").map(String.init)
        var flags: [String: String] = [:]
        for f in qa.dropFirst() { let kv = f.split(separator: "=", maxSplits: 1).map(String.init); flags[kv[0]] = kv.count > 1 ? kv[1] : "1" }
        // a switch lives for one launch only: clear the last launch's
        for k in UserDefaults.standard.dictionaryRepresentation().keys where k.hasPrefix("qa.") { UserDefaults.standard.removeObject(forKey: k) }
        for (k, v) in flags where k != "status" && k != "sheet" { UserDefaults.standard.set(v, forKey: "qa." + k) }
        // the screenshots' own report (fresh, old, none), never the site's
        qaStatus = flags["status"]
        qaWeather = flags["weather"]
        // a pinned moment in Gudauri time ("2027-01-12T10:00"), for the boards that depend on the trip's stage
        qaNow = flags["now"].flatMap { Forecast.millis(local: $0) }.map { Date(timeIntervalSince1970: Double($0) / 1000) }
        forecast = qaWeather == "sample" ? WeatherSource.sample(now: qaNow.map { Int64($0.timeIntervalSince1970 * 1000) } ?? now) : qaWeather != nil ? nil : WeatherSource.cached()
        status = LiftStatus(names: names, report: qaStatus.map { StatusSource.qa($0, now: now) } ?? StatusSource.cached(), now: now)
        // the screenshots on GitHub only (a launch argument): a made-up trip, never the crew's flight
        trip = UserDefaults.standard.string(forKey: "qaTrip") == "1" ? Self.sample : Trip.from(json: UserDefaults.standard.data(forKey: "trip"))
        // and the screen to open at start, for the screenshots (app/ios/qa/shots)
        if let r = qa.first.flatMap(Route.init(qa:)) { path = [r] }
        statusSheet = flags["sheet"] == "status"
    }

    /// A made-up trip for the screenshots (invented flight numbers, as Android's QA).
    static let sample = Trip(Leg(Day(2027, 1, 10)!, flight: "XX 101", from: "TLV", to: "TBS", departs: ClockTime(16, 0)!, arrives: ClockTime(20, 35)!),
                             Leg(Day(2027, 1, 15)!, flight: "XX 102", from: "TBS", to: "TLV", departs: ClockTime(1, 35)!, arrives: ClockTime(2, 15)!))

    private let qaStatus: String?
    private let qaWeather: String?
    private let qaNow: Date?

    /// Now, or the screenshots' pinned moment.
    func clock(_ d: Date) -> Date { qaNow ?? d }

    func weather(_ now: Date) -> WeatherNow { WeatherNow(forecast: forecast, now: Int64(now.timeIntervalSince1970 * 1000)) }

    /// A link of the site opened in the app (a universal link, once the site's apple-app-site-association exists):
    /// a run (#map/run/<key>) or a meeting point (#meet/<station>/<HHMM>/<YYYYMMDD>); anything else stays home.
    func open(_ url: URL) {
        if let l = MeetLink.parse(url: url.absoluteString) {
            let arg = [l.station, l.time, l.day].compactMap { $0 }.joined(separator: "/")
            path = [.meet(station: arg.isEmpty ? nil : arg)]
            return
        }
        guard let f = URLComponents(url: url, resolvingAgainstBaseURL: false)?.fragment else { return }
        let parts = f.split(separator: "/", maxSplits: 2).map(String.init)
        if parts.first == "map" {
            let key = parts.count == 3 && parts[1] == "run" ? parts[2].removingPercentEncoding : nil
            path = [.map(run: key.flatMap { k in runs?.pistes.contains { $0.key == k } == true ? k : nil })]
        }
    }

    func go(_ r: Route) { path.append(r) }

    /// Reads the lift report while the app is open: every five minutes in season, at most once a day out of it (the
    /// site's and Android's rule); the status is also re-dated each minute, so a report goes stale on time.
    func pollStatus() async {
        var last: Int64 = 0
        while !Task.isCancelled {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            var report = status.report
            if qaStatus == nil, now - last >= LiftStatus.everyMs {
                last = now
                let month = Calendar(identifier: .gregorian).component(.month, from: Date())
                report = await StatusSource.load(inSeason: LiftStatus.inSeason(month: month), now: now)
            }
            status = LiftStatus(names: status.names, report: report, now: now)
            if qaWeather == nil, let f = await WeatherSource.load(now: now) { forecast = f }
            try? await Task.sleep(for: .seconds(60))
        }
    }
}

/// The site's address: data, links that open in the app, the privacy page.
enum Site {
    static let base = URL(string: "https://gudauri-ski-trip.vercel.app")!
    static func privacy(_ lang: Lang) -> URL { URL(string: "https://gudauri-ski-trip.vercel.app/privacy#\(lang.rawValue)")! }
}
