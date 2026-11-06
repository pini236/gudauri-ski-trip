import Foundation

/// The site's list of resorts (site/data/resorts.json, `schema: 1`), read only for what the app needs today: which
/// features a resort has. A feature behind it (docs/ARCHITECTURE.md m-8, answer 7: `dayrec`) shows only when the site
/// lists it. Unknown fields are ignored.
public struct Resorts: Decodable, Sendable {
    public struct Resort: Decodable, Sendable {
        public let id: String
        public let tz: String?
        public let features: [String]
    }

    public let resorts: [Resort]

    public static func decode(_ data: Data) throws -> Resorts { try JSONDecoder().decode(Resorts.self, from: data) }

    public func has(_ feature: String, in resort: String = "gudauri") -> Bool {
        resorts.first { $0.id == resort }?.features.contains(feature) ?? false
    }
}
