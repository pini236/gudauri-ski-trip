import Foundation

/// Who this phone is to the server (docs/USERS.md): a guest is an anonymous identity, made only when a guest first
/// needs the server (joining a group); Apple and Google later, on the same identity. As Android's server/Auth.kt.
public struct Session: Sendable, Equatable, Codable {
    public let accessToken: String
    public let refreshToken: String
    /// Seconds since 1970, from the server.
    public let expiresAt: Int64
    public let userId: String
    public let anonymous: Bool

    public init(accessToken: String, refreshToken: String, expiresAt: Int64, userId: String, anonymous: Bool) {
        self.accessToken = accessToken; self.refreshToken = refreshToken; self.expiresAt = expiresAt; self.userId = userId; self.anonymous = anonymous
    }

    /// Supabase Auth's answer: access_token, refresh_token, expires_at (or expires_in), user {id, is_anonymous}.
    static func fromAuth(_ o: JSON, now: Int64) throws -> Session {
        guard let user = o["user"], let id = user.str("id"), let access = o.str("access_token"), let refresh = o.str("refresh_token") else {
            throw ServerError(ServerClient.badResponse, 200)
        }
        let at = Int64(o.int("expires_at") ?? 0)
        let expires = at > 0 ? at : now + Int64(o.int("expires_in") ?? 3600)
        return Session(accessToken: access, refreshToken: refresh, expiresAt: expires, userId: id, anonymous: user.bool("is_anonymous"))
    }
}

/// Where the session is kept between launches (the app: the Keychain, only on this phone).
public protocol SessionStore: Sendable {
    func load() -> Session?
    func save(_ s: Session?)
}

public final class MemorySessionStore: SessionStore, @unchecked Sendable {
    private let lock = NSLock()
    private var s: Session?
    public init(_ s: Session? = nil) { self.s = s }
    public func load() -> Session? { lock.withLock { s } }
    public func save(_ s: Session?) { lock.withLock { self.s = s } }
}

/// The server (decision 40, server/CONTRACT.md): Supabase in the EU, three doors over plain HTTPS with JSON, as Android's
/// server/Server.kt and Auth.kt in one actor (the session is renewed once at a time):
/// - Auth (`/auth/v1`): the session.
/// - The tables (`/rest/v1`): reads, filtered by the row rules; direct writes only to trips, meetups and the profile.
/// - The server's code (`/functions/v1/api/<action>`): every other write, one action per request.
/// Nothing here runs by itself: every request comes from something the person did. The URL and key are public by design.
public actor ServerClient {
    public static let region = "eu-central-1"
    /// A success whose body is not the JSON it should be (a proxy's page, a cut answer): never a crash.
    public static let badResponse = "bad_response"
    /// The actions a guest may start with, before the phone has any session.
    public static let guestActions: Set<String> = ["invite_preview", "join_group", "request_reclaim"]

    public nonisolated let url: String
    public nonisolated let key: String
    private let store: SessionStore
    private let transport: Transport
    private let clock: @Sendable () -> Int64
    private var refreshing: Task<Session?, Error>?
    /// Told after every new token (Realtime re-sends it to its channels).
    private var onToken: (@Sendable () -> Void)?

    public init(url: String, key: String, store: SessionStore, transport: Transport = URLSessionTransport(),
                clock: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970) }) {
        self.url = url; self.key = key; self.store = store; self.transport = transport; self.clock = clock
    }

    public func setOnToken(_ f: (@Sendable () -> Void)?) { onToken = f }

    public nonisolated func nowSeconds() -> Int64 { clock() }

    /// Who this phone is, from what is stored: no network.
    public nonisolated func userId() -> String? { store.load()?.userId }
    public nonisolated func stored() -> Session? { store.load() }

    // The function runs in the EU only when asked: about 0.55 s instead of 1.6 s from the US.
    private nonisolated func headers(_ token: String?) -> [String: String] {
        var h = ["apikey": key, "Content-Type": "application/json", "Accept": "application/json", "x-region": Self.region]
        if let token { h["Authorization"] = "Bearer \(token)" }
        return h
    }

    // MARK: - Auth

    /// The session, renewed if it ends within a minute; nil when this phone has never needed the server.
    public func current() async throws -> Session? {
        guard let s = store.load() else { return nil }
        if s.expiresAt - clock() > 60 { return s }
        return try await refresh()
    }

    /// The session, made anonymously if there is none yet: only from an action the person took (joining a group).
    public func currentOrGuest() async throws -> Session {
        if let s = try await current() { return s }
        return try await signInAnonymously()
    }

    public func signInAnonymously() async throws -> Session {
        try keep(try await auth("signup", .obj(["data": .object([:])])))
    }

    /// Sign in with Apple or Google's ID token. A guest is linked instead, so the same identity keeps its groups and
    /// trips; when that account already has one, the server answers "identity_already_exists" and the caller merges.
    public func signInWithIdToken(provider: String, idToken: String, nonce: String?, link: Bool = true) async throws -> Session {
        var body: [String: JSON] = ["provider": .string(provider), "id_token": .string(idToken)]
        if let nonce { body["nonce"] = .string(nonce) }
        let guest = try await current().flatMap { $0.anonymous && link ? $0 : nil }
        if guest != nil { body["link_identity"] = true }
        return try keep(try await auth("token?grant_type=id_token", .object(body), token: guest?.accessToken))
    }

    /// A new token from the refresh token; nil (and forgotten) when the server says the session is over. One at a time.
    public func refresh() async throws -> Session? {
        if let t = refreshing { return try await t.value }
        let t = Task<Session?, Error> { try await self.doRefresh() }
        refreshing = t
        defer { refreshing = nil }
        return try await t.value
    }

    private func doRefresh() async throws -> Session? {
        guard let s = store.load() else { return nil }
        do {
            return try keep(try await auth("token?grant_type=refresh_token", .obj(["refresh_token": .string(s.refreshToken)])))
        } catch let e as ServerError {
            // only "this session is over" ends it: a busy server (429), a timeout (408) or a hiccup must not sign out
            if e.status == 400 || e.status == 401 || ["invalid_grant", "refresh_token_not_found", "session_not_found"].contains(e.code) {
                store.save(nil); return nil
            }
            throw e
        }
    }

    /// Forget the session on this phone (after deleting the account, or signing out).
    public func forget() { store.save(nil) }

    private func keep(_ o: JSON) throws -> Session {
        let s = try Session.fromAuth(o, now: clock())
        store.save(s)
        onToken?()
        return s
    }

    private func auth(_ path: String, _ body: JSON, token: String? = nil) async throws -> JSON {
        let r = try await transport.send(HTTPRequest("POST", "\(url)/auth/v1/\(path)", headers(token), body.text))
        if !r.ok { throw ServerError(Self.authCode(r), r.status) }
        return try Self.obj(r)
    }

    // MARK: - The server's code

    /// One action of the server's code: the answer's JSON, or [ServerError] with its code. The join actions answer
    /// {"status": ...} instead of an error; only they make a guest identity when there is no session yet.
    public func call(_ action: String, _ body: JSON = .object([:])) async throws -> JSON {
        let r = try await send(action, body)
        return r.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .object([:]) : try Self.obj(r)
    }

    /// An action whose answer is a list (group_leaderboard).
    public func callList(_ action: String, _ body: JSON = .object([:])) async throws -> [JSON] {
        try Self.list(try await send(action, body))
    }

    private func send(_ action: String, _ body: JSON) async throws -> HTTPResponse {
        precondition(action.allSatisfy { $0 == "_" || ("a"..."z").contains($0) }, action)
        let r = try await authorized(guest: Self.guestActions.contains(action)) { t in
            HTTPRequest("POST", "\(self.url)/functions/v1/api/\(action)", self.headers(t), body.text)
        }
        if !r.ok { throw ServerError(Self.code(r), r.status) }
        return r
    }

    // MARK: - The tables

    /// Rows the row rules let me see: [query] is PostgREST's ("select=id,name&group_id=eq.<id>").
    public func select(_ table: String, _ query: String) async throws -> [JSON] {
        let r = try await authorized { t in HTTPRequest("GET", "\(self.url)/rest/v1/\(table)?\(query)", self.headers(t)) }
        if !r.ok { throw ServerError(Self.code(r), r.status) }
        return try Self.list(r)
    }

    /// A new row, as the server stored it.
    public func insert(_ table: String, _ row: JSON) async throws -> JSON {
        guard let o = try await post(table, row, "return=representation") else { throw ServerError("empty_answer", 200) }
        return o
    }

    /// Like [insert], for a row that carries an id the phone chose: sending it again changes nothing, and the answer
    /// is then nil (the caller reads the row it already made).
    public func insertOnce(_ table: String, _ row: JSON) async throws -> JSON? {
        try await post(table, row, "return=representation,resolution=ignore-duplicates")
    }

    private func post(_ table: String, _ row: JSON, _ prefer: String) async throws -> JSON? {
        let r = try await authorized { t in
            var h = self.headers(t); h["Prefer"] = prefer
            return HTTPRequest("POST", "\(self.url)/rest/v1/\(table)", h, row.text)
        }
        if !r.ok { throw ServerError(Self.code(r), r.status) }
        return try Self.list(r).first
    }

    /// Change the row with this id; nil when the rules hide it (gone, or not mine).
    public func update(_ table: String, id: String, _ row: JSON) async throws -> JSON? {
        let r = try await authorized { t in
            var h = self.headers(t); h["Prefer"] = "return=representation"
            return HTTPRequest("PATCH", "\(self.url)/rest/v1/\(table)?id=eq.\(Self.enc(id))", h, row.text)
        }
        if !r.ok { throw ServerError(Self.code(r), r.status) }
        return try Self.list(r).first
    }

    public func delete(_ table: String, id: String) async throws {
        let r = try await authorized { t in HTTPRequest("DELETE", "\(self.url)/rest/v1/\(table)?id=eq.\(Self.enc(id))", self.headers(t)) }
        if !r.ok { throw ServerError(Self.code(r), r.status) }
    }

    // With the session's token; a token the server refuses is renewed once. A session the server dropped is never
    // silently replaced by a new guest: that would quietly leave every group (the app offers "I'm already in the group").
    private func authorized(guest: Bool = false, _ make: @Sendable (String) -> HTTPRequest) async throws -> HTTPResponse {
        let s = try await (guest ? currentOrGuest() : current())
        guard let s else { throw ServerError("no_session", 401) }
        let r = try await transport.send(make(s.accessToken))
        if r.status != 401 { return r }
        guard let fresh = try await refresh() else { throw ServerError("no_session", 401) }
        return try await transport.send(make(fresh.accessToken))
    }

    // MARK: - Helpers

    static func obj(_ r: HTTPResponse) throws -> JSON {
        guard let j = JSON.parse(r.body), j.object != nil else { throw ServerError(badResponse, r.status) }
        return j
    }

    static func list(_ r: HTTPResponse) throws -> [JSON] {
        guard let j = JSON.parse(r.body), let a = j.array else { throw ServerError(badResponse, r.status) }
        return a
    }

    public static func enc(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s
    }

    // {"error": "not_admin"} from the server's code. From the tables {"code", "message"}: a guard's refusal (P0001)
    // carries its own short code in the message; bad values and the row rules get the server's codes (CONTRACT, "שגיאות").
    static func code(_ r: HTTPResponse) -> String {
        guard let o = JSON.parse(r.body), o.object != nil else { return "http_\(r.status)" }
        if let e = o.str("error") { return e }
        guard let c = o.str("code") else { return "http_\(r.status)" }
        switch c {
        case "P0001":
            let m = o.string("message")
            return !m.isEmpty && m.allSatisfy({ $0 == "_" || ("a"..."z").contains($0) }) ? m : c
        case "23514", "22P02", "22007", "22008", "23502": return "invalid_input"
        case "42501": return "not_allowed"
        case "23503": return "conflict"
        default: return c
        }
    }

    // Auth answers {"error_code": "..."} (or {"error": "invalid_grant"} on older servers).
    static func authCode(_ r: HTTPResponse) -> String {
        guard let o = JSON.parse(r.body) else { return "http_\(r.status)" }
        return o.str("error_code") ?? o.str("error") ?? "http_\(r.status)"
    }
}

/// The live server's public address and key (server/CONTRACT.md, "כתובת ומפתח"): public by design, as on Android.
public enum ServerConfig {
    public static let url = "https://vanuhuzuhnljvcoihvys.supabase.co"
    public static let key = "sb_publishable_BpJiIkRbIob7U6xa_Xt_9A_AJqtoupz"
}
