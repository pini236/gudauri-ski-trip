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
    /// green, blue, red, black; "none" for a ski route (`kind: ski-route`).
    public let color: String
    public let named: Bool
    /// Horizontal length in metres.
    public let len: Double
    /// `run`, `ski-way`, `beginner-area` or `ski-route`; nil is a run.
    public let kind: String?
    public let segs: [Segment]
    public let fromLifts: [Int]
    public let toLifts: [Int]

    public struct Segment: Decodable, Sendable {
        public let g: [[Double]]
    }

    enum CodingKeys: String, CodingKey { case key, name, color, named, len, kind, segs, fromLifts, toLifts }

    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? key
        color = try c.decodeIfPresent(String.self, forKey: .color) ?? "none"
        named = try c.decodeIfPresent(Bool.self, forKey: .named) ?? true
        len = try c.decodeIfPresent(Double.self, forKey: .len) ?? 0
        kind = try c.decodeIfPresent(String.self, forKey: .kind)
        segs = try c.decodeIfPresent([Segment].self, forKey: .segs) ?? []
        fromLifts = (try? c.decodeIfPresent([Int].self, forKey: .fromLifts)) ?? []
        toLifts = (try? c.decodeIfPresent([Int].self, forKey: .toLifts)) ?? []
    }
}

public struct Lift: Decodable, Sendable, Identifiable {
    public let id: Int
    public let name: String
    public let kind: String
    public let g: [[Double]]
    public let len: Double

    enum CodingKeys: String, CodingKey { case id, name, kind, g, len }

    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        kind = try c.decodeIfPresent(String.self, forKey: .kind) ?? ""
        g = try c.decodeIfPresent([[Double]].self, forKey: .g) ?? []
        len = try c.decodeIfPresent(Double.self, forKey: .len) ?? 0
    }
}
