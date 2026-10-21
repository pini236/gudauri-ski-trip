import Foundation
import Security
import GudiDomain

/// The app's one door to accounts and groups (13.5 on iPhone): the real server through GudiDomain's client (the
/// session in the Keychain, the group copies in the app's private files), or, in the screenshots only, the pretend
/// server with made-up people (`-qaRoute "grp:...+group=admin"`, as Android's debug DevServer).
///
/// Other parts use it through these, and nothing else:
/// - `GroupService.shared.submitBest(game:score:)`: a game's high score to the group tables (Q9, the contract's "השיאים").
/// - `GroupService.shared.addMeetup(groupId:station:at:)` and `myGroups()`: the meeting point saves a meetup (Q8).
/// - `GroupService.route(for:)`: an invite link (`/j/<token>`, `/join/<code>`) as a route, for the root's onOpenURL.
/// - `tripSaved(_:)`: called by AppModel after "your trip" is saved, so a group shows it.
@MainActor
final class GroupService {
    static let shared = GroupService()

    let api: GroupAPI
    /// True with the pretend server (the screenshots).
    let fake: Bool
    private var live: LiveGroupAPI?

    /// The games with a high-score table (Q9), by their key on the server and the site.
    static let games = ["descent", "school", "fresh", "snowball", "merge"]

    private init() {
        let d = UserDefaults.standard
        if let seed = d.string(forKey: "qa.group") {
            let l = Lang(rawValue: d.string(forKey: "lang") ?? "") ?? Lang.auto(Locale.preferredLanguages)
            api = FakeGroupAPI(seed: seed, hebrew: l == .he)
            fake = true
            return
        }
        fake = false
        let base = (try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? FileManager.default.temporaryDirectory
        let dir = base.appendingPathComponent("server", isDirectory: true)
        // the group copies are private and never backed up (as Android's backup off)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var u = dir
        var rv = URLResourceValues(); rv.isExcludedFromBackup = true
        try? u.setResourceValues(rv)
        let server = ServerClient(url: ServerConfig.url, key: ServerConfig.key, store: KeychainSessionStore())
        let realtime = Realtime(server: server, sockets: WebSocketFactory())
        Task { await server.setOnToken { Task { await realtime.tokenChanged() } } }
        let sync = Sync(server: server, dir: dir.appendingPathComponent("groups", isDirectory: true), games: Self.games, realtime: realtime)
        let trips = TripSync(server: server, file: dir.appendingPathComponent("trip.json"))
        let l = LiveGroupAPI(server: server, sync: sync, tripSync: trips, profileFile: dir.appendingPathComponent("me.json"),
                             lang: { Strings.shared.lang.rawValue })
        live = l
        api = l
    }

    /// On launch and when the app comes back: what waits goes out.
    func flush() async { await live?.flush() }

    func tripSaved(_ trip: Trip?) { Task { await api.tripSaved(trip) } }

    /// A game's high score to the groups' tables; false when I am in no group (then nothing is sent).
    @discardableResult
    func submitBest(game: String, score: Int) async -> Bool { await api.submitBest(game: game, score: score) }

    func myGroups() async -> [GroupSummary] { (try? await api.myGroups()) ?? [] }

    func addMeetup(groupId: String, station: String, at: Date) async throws { try await api.addMeetup(groupId, station: station, at: at) }

    /// An invite link of the site that opens here (A-31): `/j/<token>` or `/join/<code>`, also `#join/<code>`.
    static func route(for url: URL) -> Route? {
        let s = url.absoluteString
        if let code = InviteCode.fromLink(s) { return .groups(.join(code)) }
        if let f = url.fragment, f.hasPrefix("join/") {
            let c = InviteCode.clean(String(f.dropFirst(5)))
            if InviteCode.isCode(c) { return .groups(.join(c)) }
        }
        return nil
    }
}

/// The session only on this phone, in the Keychain (this device only, after the first unlock: never synced to iCloud).
final class KeychainSessionStore: SessionStore, @unchecked Sendable {
    private let lock = NSLock()
    private let account = "session"
    private let service = "io.github.pini236.skiapp.server"

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    func load() -> Session? {
        lock.withLock {
            var q = query
            q[kSecReturnData as String] = true
            q[kSecMatchLimit as String] = kSecMatchLimitOne
            var out: AnyObject?
            guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
            return try? JSONDecoder().decode(Session.self, from: d)
        }
    }

    func save(_ s: Session?) {
        lock.withLock {
            SecItemDelete(query as CFDictionary)
            guard let s, let d = try? JSONEncoder().encode(s) else { return }
            var q = query
            q[kSecValueData as String] = d
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(q as CFDictionary, nil)
        }
    }
}

/// The real WebSocket for Realtime (the system's, no library), as Android's OkHttpSockets.
struct WebSocketFactory: SocketFactory {
    func open(_ url: String, _ listener: SocketListener) -> Socket {
        let task = URLSession.shared.webSocketTask(with: URL(string: url)!)
        let s = WebSocket(task: task, listener: listener)
        s.start()
        return s
    }
}

final class WebSocket: Socket, @unchecked Sendable {
    private let task: URLSessionWebSocketTask
    private let listener: SocketListener
    private let lock = NSLock()
    private var closed = false

    init(task: URLSessionWebSocketTask, listener: SocketListener) { self.task = task; self.listener = listener }

    func start() {
        task.resume()
        // the socket opens with its first exchange; a ping both checks it and says "open"
        task.sendPing { [weak self] err in
            guard let self else { return }
            if err != nil { self.end() } else { self.listener.onOpen(); self.receive() }
        }
    }

    private func receive() {
        task.receive { [weak self] r in
            guard let self else { return }
            switch r {
            case .success(.string(let t)): self.listener.onMessage(t); self.receive()
            case .success(.data(let d)): self.listener.onMessage(String(decoding: d, as: UTF8.self)); self.receive()
            case .success: self.receive()
            case .failure: self.end()
            }
        }
    }

    private func end() {
        let first = lock.withLock { () -> Bool in defer { closed = true }; return !closed }
        if first { listener.onClosed() }
    }

    func send(_ text: String) { task.send(.string(text)) { [weak self] e in if e != nil { self?.task.cancel(); self?.end() } } }

    func close() {
        lock.withLock { closed = true }
        task.cancel(with: .normalClosure, reason: nil)
    }
}
