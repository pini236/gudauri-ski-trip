import Foundation

/// Food and services near the stations (round 31, approved 1.11.2026): the lean `places.json` the ski expert builds from the
/// open map (research/places/places.lean.json, copied to site/data/places.json). Read as the site reads it (site/js/app.js,
/// PL) and as Android does (places/Places.kt): every station has the places inside 250 m, nearest first, or `info: "none"`,
/// and "none" means there is no information, never "there is nothing here". A kind this app does not know is ignored, a file
/// with a schema other than 1 is not used, and nothing here says when anything is open.
public struct Place: Sendable, Equatable {
    public let id: String, kind: String
    public let name: String?
    public let lat: Double, lon: Double
    public let seasonal: Bool, slope: Bool
}

/// A place near a station, and how far, in metres.
public struct Near: Sendable, Equatable {
    public let place: Place
    public let d: Int
}

/// [liftId]: the lift's OSM id as text; [bottom]: the lift's lower end (otherwise its top).
public struct StationEnd: Sendable, Equatable {
    public let liftId: String
    public let bottom: Bool
}

public struct PlaceStation: Sendable {
    public let id: String
    public let lat: Double, lon: Double
    public let ends: [StationEnd]
    public let info: String
    let refs: [(id: String, d: Int)]
}

public struct Places: Sendable {
    /// The kinds the app shows, in the order of the dots.
    public static let kinds = ["food", "cafe", "aid", "wc"]
    /// The feature's name in resorts.json "features" (the site's `has('places')`).
    public static let feature = "places"

    public let stations: [PlaceStation]
    public let places: [Place]
    private let byId: [String: Place]

    init(stations: [PlaceStation], places: [Place], byId: [String: Place]) { self.stations = stations; self.places = places; self.byId = byId }

    /// The file, or nil when it is not the structure this app knows (another schema, a broken file): then there are no places.
    public static func parse(_ data: Data) -> Places? {
        guard let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], (o["schema"] as? Int) == 1,
              let ps = o["places"] as? [[String: Any]], let ss = o["stations"] as? [[String: Any]] else { return nil }
        var places: [Place] = []
        for p in ps {
            guard let id = p["id"] as? String, let ll = p["ll"] as? [Double], ll.count == 2 else { return nil }
            let name = (p["name"] as? String).flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
            places.append(Place(id: id, kind: p["kind"] as? String ?? "", name: name, lat: ll[0], lon: ll[1],
                                seasonal: p["seasonal"] as? Bool ?? false, slope: p["slope"] as? Bool ?? false))
        }
        var stations: [PlaceStation] = []
        for s in ss {
            guard let id = s["id"] as? String, let ll = s["ll"] as? [Double], ll.count == 2, let ends = s["ends"] as? [[String: Any]] else { return nil }
            let es: [StationEnd] = ends.compactMap { e in
                guard let lid = e["liftId"], let end = e["end"] as? String else { return nil }
                return StationEnd(liftId: "\(lid)", bottom: end == "bottom")
            }
            let refs = (s["places"] as? [[String: Any]] ?? []).compactMap { r -> (String, Int)? in
                guard let p = r["p"] as? String else { return nil }
                return (p, (r["d"] as? Int) ?? Int((r["d"] as? Double) ?? 0))
            }
            stations.append(PlaceStation(id: id, lat: ll[0], lon: ll[1], ends: es, info: s["info"] as? String ?? "", refs: refs))
        }
        return Places(stations: stations, places: places, byId: Dictionary(places.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a }))
    }

    /// The station at one end of a lift, or nil for a lift that is on none (the unnamed conveyor and the like).
    public func stationOf(liftId: String, bottom: Bool) -> PlaceStation? {
        stations.first { s in s.ends.contains { $0.liftId == liftId && $0.bottom == bottom } }
    }

    /// The places the station has, nearest first, that this app knows how to show.
    public func near(_ s: PlaceStation) -> [Near] {
        guard s.info == "some" else { return [] }
        return s.refs.compactMap { r in byId[r.id].flatMap { Self.kinds.contains($0.kind) ? Near(place: $0, d: r.d) : nil } }
    }

    /// The stations that have something to show, with the kinds they have in the order of `kinds`.
    public func withPlaces() -> [(station: PlaceStation, kinds: [String])] {
        stations.compactMap { s in
            let n = near(s)
            return n.isEmpty ? nil : (s, Self.kinds.filter { k in n.contains { $0.place.kind == k } })
        }
    }

    /// A café or a restaurant on the slope belongs to no station.
    public func onSlope() -> [Place] { places.filter { $0.slope && Self.kinds.contains($0.kind) } }
}
