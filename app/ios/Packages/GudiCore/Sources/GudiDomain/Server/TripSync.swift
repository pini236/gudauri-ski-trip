import Foundation

/// A few strings kept in one small file in the app's private storage (written whole, then swapped in).
final class FileKV: @unchecked Sendable {
    private let url: URL
    private let lock = NSLock()
    private var values: [String: String]

    init(_ url: URL) {
        self.url = url
        values = (try? JSONDecoder().decode([String: String].self, from: Data(contentsOf: url))) ?? [:]
    }

    subscript(key: String) -> String? {
        get { lock.withLock { values[key] } }
        set { lock.withLock { values[key] = newValue; save() } }
    }

    func removeAll() { lock.withLock { values = [:]; try? FileManager.default.removeItem(at: url) } }

    private func save() { FileStore.write(url, (try? JSONEncoder().encode(values)) ?? Data()) }
}

enum FileStore {
    // write a copy, then swap: a crash mid-write never leaves half a file
    static func write(_ url: URL, _ data: Data) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let tmp = url.appendingPathExtension("tmp")
        do {
            try data.write(to: tmp)
            _ = try? FileManager.default.removeItem(at: url)
            try FileManager.default.moveItem(at: tmp, to: url)
        } catch {
            try? data.write(to: url)
        }
    }
}

/// "Your trip" on the server too, so a group can show it (Q7), as Android's server/TripSync.kt: only for a phone that
/// already has a session (it joined or made a group); a guest who never needed the server stays off it. Without signal
/// the latest trip waits and goes with the next [flush]. Keeps the server's id of my trip, so it is one row that changes.
public actor TripSync {
    private let server: ServerClient
    private let kv: FileKV
    private static let deleted = "deleted"

    public init(server: ServerClient, file: URL) {
        self.server = server
        kv = FileKV(file)
    }

    /// The server's id of my trip, to show it in a group.
    public nonisolated var tripId: String? { kv["id"] }

    /// After every save on the phone (nil: "delete the trip", which also deletes the row on the server, ד2).
    public func pushed(_ trip: Trip?) async {
        kv["pending"] = trip.map { String(decoding: $0.json(), as: UTF8.self) } ?? Self.deleted
        try? await send()
    }

    public func flush() async { try? await send() }

    /// Now: my trip on the server, and its id, to show it in a group.
    public func sendNow(_ trip: Trip) async throws -> String {
        kv["pending"] = String(decoding: trip.json(), as: UTF8.self)
        try await send()
        guard let id = tripId else { throw ServerError("no_session", 401) }
        return id
    }

    /// The server made or chose my trip row itself ("I'm on the same flight"): from now on that row is my trip. A
    /// delete still waiting is older than this, so it goes too (sent later, it would delete the row just adopted).
    public func adopt(_ id: String, trip: Trip) {
        kv["id"] = id
        let p = kv["pending"]
        if p == String(decoding: trip.json(), as: UTF8.self) || p == Self.deleted { kv["pending"] = nil }
    }

    /// Signed out, or the account deleted: the row and anything waiting belonged to that account.
    public func forget() { kv.removeAll() }

    private func send() async throws {
        guard let pending = kv["pending"] else { return }
        guard try await server.current() != nil else { return }
        let id = tripId
        if pending == Self.deleted {
            if let id { try await server.delete("trips", id: id) }
            kv["id"] = nil; kv["pending"] = nil
            return
        }
        guard let trip = Trip.from(json: Data(pending.utf8)) else { kv["pending"] = nil; return }
        let row = TripRow.of(trip)
        var saved: JSON? = nil
        if let id { saved = try await server.update("trips", id: id, row) }
        if saved == nil { saved = try await server.insert("trips", row) }
        if let sid = saved?.str("id") { kv["id"] = sid }
        // only what was sent: a newer save while this one travelled stays pending
        if kv["pending"] == pending { kv["pending"] = nil }
    }
}
