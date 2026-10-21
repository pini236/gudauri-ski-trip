import Foundation

/// A video of a run (site/data/videos-seed.json, made by tools/build-videos.py; Android's data/Videos.kt): only videos
/// whose title or description names the run with its number are in the file.
public struct Video: Sendable, Identifiable {
    public var id: String { url }
    public let piste: String, url: String, title: String, channel: String, length: String, by: String
    public let at: Int64

    /// The YouTube id of a youtube.com or youtu.be link (the site's ytId), for the thumbnail; nil for any other link.
    public var youtubeId: String? {
        guard let u = URLComponents(string: url), let host = u.host else { return nil }
        let id: String?
        if host == "youtu.be" { id = String(u.path.dropFirst()) }
        else if host == "youtube.com" || host.hasSuffix(".youtube.com") { id = u.queryItems?.first { $0.name == "v" }?.value }
        else { id = nil }
        guard let id, id.count == 11, id.allSatisfy({ $0.isLetter && $0.isASCII || $0.isNumber || $0 == "_" || $0 == "-" }) else { return nil }
        return id
    }

    public static func parse(_ data: Data) -> [Video] {
        guard let a = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return a.map { v in
            func s(_ k: String) -> String { v[k] as? String ?? "" }
            return Video(piste: s("piste"), url: s("url"), title: s("title"), channel: s("channel"), length: s("length"), by: s("by"),
                         at: (v["at"] as? NSNumber)?.int64Value ?? 0)
        }.filter { $0.url.hasPrefix("http") }
    }

    /// A run's videos, the newest first (the site's vidList).
    public static func of(_ all: [Video], key: String) -> [Video] { all.filter { $0.piste == key }.sorted { $0.at > $1.at } }
}
