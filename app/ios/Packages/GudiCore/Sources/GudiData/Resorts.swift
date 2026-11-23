import Foundation

/// The site's list of resorts (site/data/resorts.json, `schema: 1`), read only for what the app needs today: which
/// features a resort has. A feature behind it (docs/ARCHITECTURE.md m-8, answer 7: `dayrec`) shows only when the site
/// lists it. Unknown fields are ignored.
public struct Resorts: Decodable, Sendable {
    public struct Resort: Decodable, Sendable {
        public let id: String
        public let tz: String?
        public let features: [String]
        /// The season's dates (round 39); nil when the resort has none. Read without failing: a field of another shape is nil.
        public let season: SeasonRaw?
    }

    /// `season` as the file writes it: strings only, never read as dates here (Domain's `Season` does, by the resort's clock).
    public struct SeasonRaw: Decodable, Sendable {
        public let open: String?
        public let announced: String?
        public let src: String?
        public let url: String?
        public let updated: String?

        enum Keys: String, CodingKey { case open, announced, src, url, updated }

        public init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: Keys.self)
            open = (try? c.decodeIfPresent(String.self, forKey: .open)) ?? nil
            announced = (try? c.decodeIfPresent(String.self, forKey: .announced)) ?? nil
            src = (try? c.decodeIfPresent(String.self, forKey: .src)) ?? nil
            url = (try? c.decodeIfPresent(String.self, forKey: .url)) ?? nil
            updated = (try? c.decodeIfPresent(String.self, forKey: .updated)) ?? nil
        }

        public init(open: String?, announced: String? = nil, src: String? = nil, url: String? = nil, updated: String? = nil) {
            self.open = open; self.announced = announced; self.src = src; self.url = url; self.updated = updated
        }
    }

    public let resorts: [Resort]

    public static func decode(_ data: Data) throws -> Resorts { try JSONDecoder().decode(Resorts.self, from: data) }

    public func has(_ feature: String, in resort: String = "gudauri") -> Bool {
        resorts.first { $0.id == resort }?.features.contains(feature) ?? false
    }
}

extension Resorts {
    /// The resort's `season` object, or nil.
    public func season(of resort: String = "gudauri") -> SeasonRaw? { resorts.first { $0.id == resort }?.season }
    public func tz(of resort: String = "gudauri") -> String? { resorts.first { $0.id == resort }?.tz }
}
