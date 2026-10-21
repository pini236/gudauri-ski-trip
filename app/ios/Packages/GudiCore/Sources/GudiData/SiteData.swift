import Foundation

/// The site's runs and lifts (site/data/runs-and-lifts.json), read-only. Coordinates are [lat, lon], as on the site.
/// Only the fields the app uses are decoded; anything else in the file is ignored, so a new field on the site never
/// breaks an installed app.
public struct RunsAndLifts: Decodable, Sendable {
    public let pistes: [Piste]
    public let lifts: [Lift]

    public static func decode(_ data: Data) throws -> RunsAndLifts {
        try JSONDecoder().decode(RunsAndLifts.self, from: data)
    }
}

public struct Piste: Decodable, Sendable, Identifiable {
    public var id: String { key }
    public let key: String
    public let name: String
    /// green, blue, red, black; "none" for a ski route (`kind: ski-route`). Missing is black, as on Android.
    public let color: String
    public let named: Bool
    /// Horizontal length in metres.
    public let len: Double
    /// `run`, `ski-way`, `beginner-area` or `ski-route`.
    public let kind: String
    public let segs: [Segment]
    /// The connections the site computed, by lift and run names (a run is open only if a lift up to it is).
    public let fromLifts: [String]
    public let toLifts: [String]
    public let fromPistes: [String]
    public let joins: [String]

    public struct Segment: Decodable, Sendable {
        public let g: [[Double]]
        public let area: Bool

        enum CodingKeys: String, CodingKey { case g, area }
        public init(from d: Decoder) throws {
            let c = try d.container(keyedBy: CodingKeys.self)
            g = try c.decodeIfPresent([[Double]].self, forKey: .g) ?? []
            area = try c.decodeIfPresent(Bool.self, forKey: .area) ?? false
        }
    }

    /// Its lines (not the areas) in projected metres, [x, y] pairs as Geo projects them, for drawing.
    public var lines: [[(x: Double, y: Double)]] {
        segs.filter { !$0.area && $0.g.count >= 2 }.map { $0.g.map { (Geo.x($0[1]), Geo.y($0[0])) } }
    }

    /// The mean latitude of all its points, areas too (the site's meanLat, for the Kobi side).
    public var lat: Double { meanLat(segs.map(\.g)) }

    enum CodingKeys: String, CodingKey { case key, name, color, named, len, kind, segs, fromLifts, toLifts, fromPistes, joins }

    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? key
        color = try c.decodeIfPresent(String.self, forKey: .color) ?? "black"
        named = try c.decodeIfPresent(Bool.self, forKey: .named) ?? false
        len = try c.decodeIfPresent(Double.self, forKey: .len) ?? 0
        kind = try c.decodeIfPresent(String.self, forKey: .kind) ?? "run"
        segs = try c.decodeIfPresent([Segment].self, forKey: .segs) ?? []
        fromLifts = try c.decodeIfPresent([String].self, forKey: .fromLifts) ?? []
        toLifts = try c.decodeIfPresent([String].self, forKey: .toLifts) ?? []
        fromPistes = try c.decodeIfPresent([String].self, forKey: .fromPistes) ?? []
        joins = try c.decodeIfPresent([String].self, forKey: .joins) ?? []
    }

    /// For tests and previews.
    public init(key: String, color: String = "blue", fromLifts: [String] = []) {
        self.key = key; name = key; self.color = color; named = true; len = 0; kind = "run"; segs = []
        self.fromLifts = fromLifts; toLifts = []; fromPistes = []; joins = []
    }
}

public struct Lift: Decodable, Sendable, Identifiable {
    /// OSM's id, as text (a number in the file).
    public let id: String
    public let name: String
    public let kind: String
    public let g: [[Double]]
    public let len: Double
    /// "inactive" for a lift that does not run.
    public let status: String?

    public var lat: Double { meanLat([g]) }

    /// Its cable in projected metres, from its first point.
    public var line: [(x: Double, y: Double)] { g.map { (Geo.x($0[1]), Geo.y($0[0])) } }

    enum CodingKeys: String, CodingKey { case id, name, kind, g, len, status }

    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        if let n = try? c.decode(Int.self, forKey: .id) { id = String(n) } else { id = (try? c.decode(String.self, forKey: .id)) ?? "" }
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        kind = try c.decodeIfPresent(String.self, forKey: .kind) ?? ""
        g = try c.decodeIfPresent([[Double]].self, forKey: .g) ?? []
        len = try c.decodeIfPresent(Double.self, forKey: .len) ?? 0
        status = try c.decodeIfPresent(String.self, forKey: .status)
    }
}

extension RunsAndLifts {
    /// The Kobi side is everything north of Kobi Pass, the top of Firni (site/js/app.js).
    public static let kobiLat = 42.5115
    /// The main side of the mountain, without Kobi (the site's mainPistes and mainLifts).
    public var mainPistes: [Piste] { pistes.filter { $0.lat <= Self.kobiLat } }
    public var mainLifts: [Lift] { lifts.filter { $0.lat <= Self.kobiLat } }
}

func meanLat(_ lines: [[[Double]]]) -> Double {
    var t = 0.0, n = 0
    for g in lines { for p in g where !p.isEmpty { t += p[0]; n += 1 } }
    return n > 0 ? t / Double(n) : 0
}
