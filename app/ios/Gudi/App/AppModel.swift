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

    /// The user's own trip, as JSON in Android's format, in UserDefaults.
    var trip: Trip? {
        didSet { UserDefaults.standard.set(trip?.json(), forKey: "trip") }
    }

    init() {
        let data = { (name: String) in Bundle.main.url(forResource: name, withExtension: "json", subdirectory: "Data") }
        runs = data("runs-and-lifts").flatMap { try? Data(contentsOf: $0) }.flatMap { try? RunsAndLifts.decode($0) }
        terrainURL = data("terrain")
        let names = runs.map { LiftStatus.names($0.mainLifts) } ?? []
        status = LiftStatus(names: names, report: nil, now: Int64(Date().timeIntervalSince1970 * 1000))
        // the screenshots on GitHub only (a launch argument): a made-up trip, never the crew's flight
        trip = UserDefaults.standard.string(forKey: "qaTrip") == "1" ? Self.sample : Trip.from(json: UserDefaults.standard.data(forKey: "trip"))
    }

    /// A made-up trip for the screenshots (invented flight numbers, as Android's QA).
    static let sample = Trip(Leg(Day(2027, 1, 10)!, flight: "XX 101", from: "TLV", to: "TBS", departs: ClockTime(16, 0)!, arrives: ClockTime(20, 35)!),
                             Leg(Day(2027, 1, 15)!, flight: "XX 102", from: "TBS", to: "TLV", departs: ClockTime(1, 35)!, arrives: ClockTime(2, 15)!))

    func go(_ r: Route) { path.append(r) }
}

/// The site's address: data, links that open in the app, the privacy page.
enum Site {
    static let base = URL(string: "https://gudauri-ski-trip.vercel.app")!
    static func privacy(_ lang: Lang) -> URL { URL(string: "https://gudauri-ski-trip.vercel.app/privacy#\(lang.rawValue)")! }
}
