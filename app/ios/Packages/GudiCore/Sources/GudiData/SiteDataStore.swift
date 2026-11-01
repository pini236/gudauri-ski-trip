import Foundation

/// The site's data in the app (docs/APP-NATIVE.md, "Data"), like Android's SiteData: everything is packaged, so the app
/// works with no signal from the first launch, and the file that changes during the season (runs and lifts) is refreshed
/// from the site without a new version. The app asks with the ETag the server already sends and downloads only what changed.
///
/// Rules, so an old app in someone's phone never breaks:
/// - A download is used only after it decodes with this app's own decoder and passes sanity checks; anything else is
///   dropped and the previous copy stays.
/// - A file may declare `"schema": n` at its top level; an app that knows a lower number ignores it.
/// - Downloads belong to the app version that fetched them; a new version starts again from its packaged copy.
/// - New data is read at the next launch, never swapped under the user's feet.
public struct SiteDataStore: Sendable {
    public struct Response: Sendable {
        public let code: Int
        public let etag: String?
        public let body: Data?
        public init(code: Int, etag: String?, body: Data?) { self.code = code; self.etag = etag; self.body = body }
    }
    public typealias Fetcher = @Sendable (URL, String?) async throws -> Response
    public enum Outcome: Sendable, Equatable { case updated, unchanged, rejected, failed, skipped }

    public static let base = "https://gudauri-ski-trip.vercel.app/data/"
    public static let everySeconds: Double = 6 * 3600
    public static let maxBytes = 4 << 20
    public static let files = ["runs-and-lifts.json"]

    private struct Entry: Codable { var etag: String?; var checked: Double }
    private struct Meta: Codable { var app: String; var files: [String: Entry] }

    let dir: URL
    let appVersion: String
    let packaged: @Sendable (String) -> Data?
    let fetch: Fetcher
    let now: @Sendable () -> Double

    public init(dir: URL, appVersion: String, packaged: @escaping @Sendable (String) -> Data?,
                fetch: @escaping Fetcher = SiteDataStore.urlSession,
                now: @escaping @Sendable () -> Double = { Date().timeIntervalSince1970 }) {
        self.dir = dir; self.appVersion = appVersion; self.packaged = packaged; self.fetch = fetch; self.now = now
    }

    /// Whether a downloaded file may be used: it decodes, is not of a newer schema, and holds a real mountain.
    public static func valid(_ name: String, _ data: Data) -> Bool {
        guard data.count <= maxBytes, name == "runs-and-lifts.json" else { return false }
        if let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
           let s = o["schema"] as? Int, s > 1 { return false }
        guard let r = try? RunsAndLifts.decode(data) else { return false }
        return r.pistes.count >= 10 && r.lifts.count >= 5 && r.pistes.allSatisfy { !$0.key.isEmpty }
            && r.pistes.filter { !$0.lines.isEmpty }.count >= 10
    }

    private var metaURL: URL { dir.appendingPathComponent("meta.json") }

    private func loadMeta() -> Meta {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let d = try? Data(contentsOf: metaURL), let m = try? JSONDecoder().decode(Meta.self, from: d), m.app == appVersion { return m }
        // a new app version: its packaged files win, and the next refresh starts from them
        for n in Self.files { try? FileManager.default.removeItem(at: dir.appendingPathComponent(n)) }
        return Meta(app: appVersion, files: [:])
    }

    private func save(_ m: Meta) {
        if let d = try? JSONEncoder().encode(m) { try? d.write(to: metaURL, options: .atomic) }
    }

    /// The newest valid copy of a data file: the downloaded one if there is one, otherwise the packaged one.
    public func read(_ name: String) -> Data? {
        var meta = loadMeta()
        let f = dir.appendingPathComponent(name)
        if let d = try? Data(contentsOf: f) {
            if Self.valid(name, d) { return d }
            try? FileManager.default.removeItem(at: f) // damaged on disk: forget it, and its ETag
            meta.files[name] = nil
            save(meta)
        }
        return packaged(name)
    }

    /// Asks the site for every updatable file (at most every 6 hours unless forced). Safe to call with no network.
    @discardableResult
    public func refresh(force: Bool = false) async -> [String: Outcome] {
        var meta = loadMeta()
        var out: [String: Outcome] = [:]
        for name in Self.files {
            let e = meta.files[name]
            if !force, let e, now() - e.checked < Self.everySeconds { out[name] = .skipped; continue }
            let f = dir.appendingPathComponent(name)
            let known = FileManager.default.fileExists(atPath: f.path) ? e?.etag : nil
            guard let url = URL(string: Self.base + name), let r = try? await fetch(url, known) else { out[name] = .failed; continue }
            if r.code == 304, known != nil {
                meta.files[name] = Entry(etag: known, checked: now()); out[name] = .unchanged
            } else if r.code == 200, let body = r.body, Self.valid(name, body) {
                if (try? body.write(to: f, options: .atomic)) != nil {
                    meta.files[name] = Entry(etag: r.etag, checked: now()); out[name] = .updated
                } else { out[name] = .failed }
            } else if r.code == 200 {
                meta.files[name] = Entry(etag: e?.etag, checked: now()); out[name] = .rejected
            } else { out[name] = .failed }
        }
        save(meta)
        return out
    }

    /// The real fetcher: a conditional GET with the ETag.
    @Sendable public static func urlSession(_ url: URL, _ etag: String?) async throws -> Response {
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        if let etag { req.setValue(etag, forHTTPHeaderField: "If-None-Match") }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let h = resp as? HTTPURLResponse
        return Response(code: h?.statusCode ?? 0, etag: h?.value(forHTTPHeaderField: "ETag"), body: data)
    }
}
