import XCTest
@testable import GudiDomain

/// A pretend server: answers by path, records every request. Never the live server.
final class FakeTransport: Transport, @unchecked Sendable {
    private let lock = NSLock()
    private var _sent: [HTTPRequest] = []
    var offline = false
    var handler: @Sendable (HTTPRequest) -> HTTPResponse

    init(_ handler: @escaping @Sendable (HTTPRequest) -> HTTPResponse) { self.handler = handler }

    var sent: [HTTPRequest] { lock.withLock { _sent } }

    func send(_ req: HTTPRequest) async throws -> HTTPResponse {
        lock.withLock { _sent.append(req) }
        if lock.withLock({ offline }) { throw Offline("test") }
        return handler(req)
    }

    func setOffline(_ v: Bool) { lock.withLock { offline = v } }
}

let authAnswer = #"{"access_token":"A2","refresh_token":"R2","expires_in":3600,"user":{"id":"u1","is_anonymous":true}}"#

final class ServerClientTests: XCTestCase {
    func session(expires: Int64 = 10_000) -> Session {
        Session(accessToken: "A1", refreshToken: "R1", expiresAt: expires, userId: "u1", anonymous: true)
    }

    func testHeadersAndRegion() async throws {
        let t = FakeTransport { _ in HTTPResponse(200, #"{"best":5}"#) }
        let s = ServerClient(url: "https://x.test", key: "K", store: MemorySessionStore(session()), transport: t, clock: { 1000 })
        let best = try await Groups(s).submitScore(game: "descent", score: 5)
        XCTAssertEqual(best, 5)
        let r = t.sent[0]
        XCTAssertEqual(r.url, "https://x.test/functions/v1/api/submit_score")
        XCTAssertEqual(r.headers["x-region"], "eu-central-1")
        XCTAssertEqual(r.headers["apikey"], "K")
        XCTAssertEqual(r.headers["Authorization"], "Bearer A1")
        XCTAssertEqual(JSON.parse(r.body!)?["score"], 5)
    }

    func testNoGuestExceptToJoin() async throws {
        let t = FakeTransport { r in
            r.url.contains("/auth/v1/signup") ? HTTPResponse(200, authAnswer) : HTTPResponse(200, #"{"status":"invalid_code"}"#)
        }
        let store = MemorySessionStore()
        let s = ServerClient(url: "https://x.test", key: "K", store: store, transport: t, clock: { 1000 })
        do { _ = try await s.call("leave_group", .obj(["group_id": "g"])); XCTFail() } catch let e as ServerError { XCTAssertEqual(e.code, "no_session") }
        XCTAssertTrue(t.sent.isEmpty, "a guest touches the server only to join")
        let p = try await Groups(s).preview("abc def")
        XCTAssertEqual(p.status, "invalid_code")
        XCTAssertEqual(t.sent.map { $0.url.replacingOccurrences(of: "https://x.test", with: "") }, ["/auth/v1/signup", "/functions/v1/api/invite_preview"])
        XCTAssertEqual(JSON.parse(t.sent[1].body!)?.string("code"), "ABCDEF")
        XCTAssertEqual(store.load()?.userId, "u1")
    }

    func testRenewsOnceOn401() async throws {
        let t = FakeTransport { r in
            if r.url.contains("refresh_token") { return HTTPResponse(200, authAnswer) }
            return r.headers["Authorization"] == "Bearer A1" ? HTTPResponse(401, #"{"error":"not_signed_in"}"#) : HTTPResponse(200, "[]")
        }
        let s = ServerClient(url: "https://x.test", key: "K", store: MemorySessionStore(session()), transport: t, clock: { 1000 })
        let rows = try await s.select("groups", "select=*")
        XCTAssertEqual(rows, [])
        XCTAssertEqual(t.sent.count, 3)
        XCTAssertEqual(t.sent[2].headers["Authorization"], "Bearer A2")
    }

    func testEndedSessionIsDroppedNotReplaced() async throws {
        let t = FakeTransport { r in r.url.contains("refresh_token") ? HTTPResponse(400, #"{"error_code":"refresh_token_not_found"}"#) : HTTPResponse(200, "[]") }
        let store = MemorySessionStore(session(expires: 1010))
        let s = ServerClient(url: "https://x.test", key: "K", store: store, transport: t, clock: { 1000 })
        do { _ = try await s.select("groups", "select=*"); XCTFail() } catch let e as ServerError { XCTAssertEqual(e.code, "no_session") }
        XCTAssertNil(store.load())
        XCTAssertFalse(t.sent.contains { $0.url.contains("signup") })
    }

    func testBusyServerKeepsTheSession() async throws {
        let t = FakeTransport { r in r.url.contains("refresh_token") ? HTTPResponse(429, "{}") : HTTPResponse(200, "[]") }
        let store = MemorySessionStore(session(expires: 1010))
        let s = ServerClient(url: "https://x.test", key: "K", store: store, transport: t, clock: { 1000 })
        do { _ = try await s.select("groups", "select=*"); XCTFail() } catch {}
        XCTAssertNotNil(store.load())
    }

    func testErrorCodes() {
        XCTAssertEqual(ServerClient.code(HTTPResponse(403, #"{"error":"not_admin"}"#)), "not_admin")
        XCTAssertEqual(ServerClient.code(HTTPResponse(400, #"{"code":"P0001","message":"too_many_trips"}"#)), "too_many_trips")
        XCTAssertEqual(ServerClient.code(HTTPResponse(400, #"{"code":"22P02","message":"bad"}"#)), "invalid_input")
        XCTAssertEqual(ServerClient.code(HTTPResponse(403, #"{"code":"42501"}"#)), "not_allowed")
        XCTAssertEqual(ServerClient.code(HTTPResponse(502, "<html>")), "http_502")
    }

    func testBadResponseIsNotACrash() async throws {
        let t = FakeTransport { _ in HTTPResponse(200, "<html>proxy</html>") }
        let s = ServerClient(url: "https://x.test", key: "K", store: MemorySessionStore(session()), transport: t, clock: { 1000 })
        do { _ = try await s.select("groups", "select=*"); XCTFail() } catch let e as ServerError { XCTAssertEqual(e.code, "bad_response") }
    }

    func testLinkIdentityForAGuest() async throws {
        let t = FakeTransport { _ in HTTPResponse(200, #"{"access_token":"G","refresh_token":"GR","expires_in":3600,"user":{"id":"u1","is_anonymous":false}}"#) }
        let store = MemorySessionStore(session())
        let s = ServerClient(url: "https://x.test", key: "K", store: store, transport: t, clock: { 1000 })
        _ = try await s.signInWithIdToken(provider: "apple", idToken: "ID", nonce: "N")
        let b = JSON.parse(t.sent[0].body!)!
        XCTAssertEqual(b.string("provider"), "apple")
        XCTAssertEqual(b.string("nonce"), "N")
        XCTAssertTrue(b.bool("link_identity"))
        XCTAssertEqual(t.sent[0].headers["Authorization"], "Bearer A1")
        XCTAssertEqual(store.load()?.anonymous, false)
    }
}

final class GroupsRulesTests: XCTestCase {
    func testNormalizeCode() {
        XCTAssertEqual(Groups.normalizeCode("kzb qrm"), "KZBQRM")
        XCTAssertEqual(Groups.normalizeCode("KZB-QRM"), "KZBQRM")
        let tok = String(repeating: "a", count: 40)
        XCTAssertEqual(Groups.normalizeCode("https://gudauri-ski-trip.vercel.app/j/\(tok)/"), tok)
        XCTAssertEqual(Groups.normalizeCode("https://x/y?t=\(tok)"), tok)
        XCTAssertEqual(Groups.normalizeCode(tok), tok)
    }

    func testInviteLinks() {
        let tok = String(repeating: "b", count: 40)
        XCTAssertEqual(InviteCode.fromLink("https://gudauri-ski-trip.vercel.app/j/\(tok)?utm_source=app"), tok)
        XCTAssertEqual(InviteCode.fromLink("https://gudauri-ski-trip.vercel.app/join/kzb-qrm"), "KZBQRM")
        XCTAssertNil(InviteCode.fromLink("KZBQRM"))
        XCTAssertEqual(InviteCode.clean("kz1bIOq rm"), "KZBQRM")
        XCTAssertTrue(InviteCode.isCode("KZBQRM"))
        XCTAssertFalse(InviteCode.isCode("KZBQRI"))
        XCTAssertEqual(InviteCode.via(tok), "link")
        XCTAssertEqual(InviteCode.via("KZBQRM"), "code")
        XCTAssertEqual(InviteCode.shared(tok), "https://gudauri-ski-trip.vercel.app/j/\(tok)?utm_source=app&utm_medium=share")
    }

    func testTripRowRoundTrip() {
        let t = Trip(Leg(Day(2027, 1, 10)!, flight: "XX 101", from: "TLV · Tel Aviv", to: "TBS · Tbilisi", departs: ClockTime(16, 0), arrives: ClockTime(20, 35)),
                     Leg(Day(2027, 1, 15)!, flight: "XX 102", from: "TBS", to: "Somewhere", departs: ClockTime(1, 35)),
                     ski: Day(2027, 1, 11)!...Day(2027, 1, 14)!)
        let row = TripRow.of(t)
        XCTAssertEqual(row.string("out_from"), "TLV")
        XCTAssertEqual(row.string("destination"), "Tbilisi")
        XCTAssertEqual(row["ret_to"], .null, "a place without a code stays on the phone")
        XCTAssertEqual(row.string("out_departs"), "16:00")
        XCTAssertEqual(row["ret_arrives"], .null)
        let back = TripRow.trip(row.with("out_departs", "16:00:00"))!
        XCTAssertEqual(back.out.fromCode, "TLV")
        XCTAssertEqual(back.out.departs, ClockTime(16, 0))
        XCTAssertEqual(back.ret?.flight, "XX 102")
        XCTAssertEqual(back.ski, Day(2027, 1, 11)!...Day(2027, 1, 14)!)
        XCTAssertNil(TripRow.trip(.obj(["out_date": nil])))
    }

    func testInviteValidity() {
        let base = JSON.obj(["id": "i", "code": "KZBQRM", "token": "t", "uses": 3, "max_uses": 3])
        XCTAssertFalse(Groups.invite(base).working(), "used up")
        let ends = base.with("max_uses", nil).with("groups", .obj(["ends_on": "2027-01-15"]))
        let inv = Groups.invite(ends)
        XCTAssertEqual(inv.validUntil.map(ISOTime.text), "2027-01-16T00:00:00Z")
        XCTAssertTrue(inv.working(ISOTime.parse("2027-01-15T23:00:00Z")!))
        XCTAssertFalse(inv.working(ISOTime.parse("2027-01-16T00:00:00Z")!))
        let made = Groups.invite(base.with("max_uses", nil).with("created_at", "2027-01-01T00:00:00+00:00"))
        XCTAssertEqual(made.validUntil.map(ISOTime.text), "2027-04-01T00:00:00Z")
    }

    func testISOTime() {
        XCTAssertEqual(ISOTime.parse("2027-01-11T09:30:00+00:00").map(ISOTime.text), "2027-01-11T09:30:00Z")
        XCTAssertEqual(ISOTime.parse("2027-01-11T09:30:00.123+04:00").map(ISOTime.text), "2027-01-11T05:30:00Z")
    }

    func testMyGroupsOrder() {
        let g = [GroupSummary(id: "a", name: "a", startsOn: nil, endsOn: nil), GroupSummary(id: "b", name: "b", startsOn: Day(2027, 2, 1), endsOn: nil),
                 GroupSummary(id: "c", name: "c", startsOn: Day(2027, 1, 1), endsOn: nil)]
        XCTAssertEqual(g.byStart().map(\.id), ["c", "b", "a"])
    }
}

/// The server as the group page reads it, for the queue's tests.
func groupServer(_ meetupsAdded: Counter) -> @Sendable (HTTPRequest) -> HTTPResponse {
    { r in
        let u = r.url
        if u.contains("/rest/v1/group_members") {
            if u.contains("select=groups") { return HTTPResponse(200, #"[{"groups":{"id":"g1","name":"G","starts_on":"2027-01-10","ends_on":null}}]"#) }
            return HTTPResponse(200, #"[{"user_id":"u1","display_name":"Me","role":"admin","trip_id":null},{"user_id":"u2","display_name":"Ann","role":"member","trip_id":"t2"}]"#)
        }
        if u.contains("/rest/v1/groups") { return HTTPResponse(200, #"[{"id":"g1","name":"G","starts_on":"2027-01-10","ends_on":null}]"#) }
        if u.contains("/rest/v1/trips") { return HTTPResponse(200, #"[{"id":"t2","owner_id":"u2","entered_by":"u1","out_date":"2027-01-10","out_flight":"XX 1","out_from":"TLV","out_to":"TBS"}]"#) }
        if u.contains("/rest/v1/meetups") && r.method == "POST" {
            meetupsAdded.add()
            let b = JSON.parse(r.body!)!
            return HTTPResponse(201, JSON.array([b.with("created_by", "u1")]).text)
        }
        if u.contains("/rest/v1/meetups") { return HTTPResponse(200, "[]") }
        if u.contains("/rest/v1/invites") { return HTTPResponse(200, "[]") }
        if u.contains("/rest/v1/join_requests") { return HTTPResponse(200, "[]") }
        if u.contains("group_leaderboard") { return HTTPResponse(200, #"[{"user_id":"u2","display_name":"Ann","best":10}]"#) }
        if u.contains("submit_score") { return HTTPResponse(200, #"{"best":50}"#) }
        if u.contains("set_my_membership") { return HTTPResponse(409, #"{"error":"trip_not_yours"}"#) }
        return HTTPResponse(404, #"{"error":"not_found"}"#)
    }
}

final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var n = 0
    func add() { lock.withLock { n += 1 } }
    var value: Int { lock.withLock { n } }
}

final class Waits: @unchecked Sendable {
    private let lock = NSLock()
    private var w: [Double] = []
    func add(_ s: Double) { lock.withLock { w.append(s) } }
    var all: [Double] { lock.withLock { w } }
}

final class SyncTests: XCTestCase {
    func tempDir() -> URL {
        let u = FileManager.default.temporaryDirectory.appendingPathComponent("sync-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    func make(_ t: FakeTransport, dir: URL, waits: Waits = Waits()) -> Sync {
        let s = ServerClient(url: "https://x.test", key: "K",
                             store: MemorySessionStore(Session(accessToken: "A", refreshToken: "R", expiresAt: 99_999, userId: "u1", anonymous: false)),
                             transport: t, clock: { 1000 })
        // a retry is recorded and never comes (the test calls flush itself)
        return Sync(server: s, dir: dir, games: ["descent"], realtime: nil, sleep: { waits.add($0); try? await Task.sleep(nanoseconds: 3_600_000_000_000) })
    }

    func testReadKeepsAndReopensOffline() async throws {
        let dir = tempDir()
        let t = FakeTransport(groupServer(Counter()))
        let sync = make(t, dir: dir)
        await sync.open("g1")
        let s = await sync.snapshot("g1")
        XCTAssertEqual(s.group?.name, "G")
        XCTAssertEqual(s.members.count, 2)
        XCTAssertEqual(s.flights["t2"]?.out.flight, "XX 1")
        XCTAssertEqual(s.enteredBy["t2"], "u1")
        XCTAssertEqual(s.scores["descent"]?.first?.best, 10)
        // a new launch with no signal: the copy on the phone, marked as not fresh
        let t2 = FakeTransport(groupServer(Counter())); t2.setOffline(true)
        let again = make(t2, dir: dir)
        await again.refresh("g1")
        let k = await again.snapshot("g1")
        XCTAssertEqual(k.group?.name, "G")
        XCTAssertEqual(k.flights["t2"]?.out.fromCode, "TLV")
        XCTAssertTrue(k.offline)
        let shown = LiveGroupAPI.shown(k, me: "u1")
        XCTAssertEqual(shown.group?.admin, true)
        XCTAssertEqual(shown.group?.members[1].enteredByAdmin, "Me")
    }

    func testQueueWaitsForSignalAndRetries() async throws {
        let dir = tempDir()
        let added = Counter()
        let t = FakeTransport(groupServer(added))
        let waits = Waits()
        let sync = make(t, dir: dir, waits: waits)
        await sync.open("g1")
        t.setOffline(true)
        let local = await sync.addMeetup("g1", station: "158744075b", at: ISOTime.parse("2027-01-11T05:30:00Z")!)
        var s = await sync.snapshot("g1")
        XCTAssertEqual(s.meetups.map(\.id), [local], "shown at once")
        XCTAssertEqual(s.waiting, 1)
        let firstWait = await sync.nextRetry
        XCTAssertEqual(firstWait, 5)
        await sync.flush()
        let second = await sync.nextRetry
        XCTAssertEqual(second, 10)
        // the queue survives a restart, and goes once signal is back; the server gets the phone's id
        t.setOffline(false)
        let again = make(t, dir: dir)
        await again.flush()
        XCTAssertEqual(added.value, 1)
        let post = t.sent.last { $0.method == "POST" && $0.url.contains("meetups") }!
        XCTAssertEqual(JSON.parse(post.body!)?.string("id"), String(local.dropFirst(6)))
        XCTAssertEqual(post.headers["Prefer"], "return=representation,resolution=ignore-duplicates")
        let q = await again.queued
        XCTAssertTrue(q.isEmpty)
        s = await again.snapshot("g1")
        XCTAssertEqual(s.waiting, 0)
        let r = await again.nextRetry
        XCTAssertEqual(r, 0)
    }

    func testRefusedIsDroppedAndSaysWhy() async throws {
        let sync = make(FakeTransport(groupServer(Counter())), dir: tempDir())
        await sync.open("g1")
        await sync.setMine("g1", displayName: "Me", tripId: "tX")
        let q = await sync.queued
        XCTAssertTrue(q.isEmpty)
        let s = await sync.snapshot("g1")
        XCTAssertEqual(s.refused, "trip_not_yours")
        await sync.clearRefused("g1")
        let c = await sync.snapshot("g1")
        XCTAssertNil(c.refused)
    }

    func testOnlyTheHigherScoreWaits() async throws {
        let t = FakeTransport(groupServer(Counter()))
        let sync = make(t, dir: tempDir())
        await sync.open("g1")
        t.setOffline(true)
        await sync.submitScore("g1", game: "descent", score: 30)
        await sync.submitScore("g1", game: "descent", score: 50)
        let q = await sync.queued
        XCTAssertEqual(q.count, 1)
        let s = await sync.snapshot("g1")
        XCTAssertEqual(s.scores["descent"]?.first?.best, 50)
        XCTAssertEqual(s.scores["descent"]?.first?.userId, "u1")
    }

    func testGoneWhenNotAMember() async throws {
        let t = FakeTransport { r in
            r.url.contains("group_members") ? HTTPResponse(200, #"[{"user_id":"u2","display_name":"Ann","role":"admin","trip_id":null}]"#) : HTTPResponse(200, "[]")
        }
        let sync = make(t, dir: tempDir())
        await sync.refresh("g1")
        let s = await sync.snapshot("g1")
        XCTAssertTrue(s.gone)
    }
}

final class TripSyncTests: XCTestCase {
    func testOneRowThatChangesAndDeletes() async throws {
        let t = FakeTransport { r in
            if r.method == "POST" { return HTTPResponse(201, #"[{"id":"T1"}]"#) }
            if r.method == "PATCH" { return HTTPResponse(200, #"[{"id":"T1"}]"#) }
            return HTTPResponse(204, "")
        }
        let s = ServerClient(url: "https://x.test", key: "K",
                             store: MemorySessionStore(Session(accessToken: "A", refreshToken: "R", expiresAt: 99_999, userId: "u1", anonymous: true)),
                             transport: t, clock: { 1000 })
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("trip-\(UUID().uuidString).json")
        let sync = TripSync(server: s, file: file)
        let trip = Trip(Leg(Day(2027, 1, 10)!, flight: "XX 1", from: "TLV", to: "TBS"))
        await sync.pushed(trip)
        await sync.pushed(trip)
        await sync.pushed(nil)
        XCTAssertEqual(t.sent.map(\.method), ["POST", "PATCH", "DELETE"])
        XCTAssertNil(sync.tripId)
    }

    func testAGuestWhoNeverJoinedStaysOff() async throws {
        let t = FakeTransport { _ in HTTPResponse(200, "[]") }
        let s = ServerClient(url: "https://x.test", key: "K", store: MemorySessionStore(), transport: t)
        let sync = TripSync(server: s, file: FileManager.default.temporaryDirectory.appendingPathComponent("trip-\(UUID().uuidString).json"))
        await sync.pushed(Trip(Leg(Day(2027, 1, 10)!)))
        XCTAssertTrue(t.sent.isEmpty)
    }
}

final class FakeSocket: Socket, @unchecked Sendable {
    private let lock = NSLock()
    private var _out: [String] = []
    var out: [String] { lock.withLock { _out } }
    func send(_ text: String) { lock.withLock { _out.append(text) } }
    func close() {}
}

final class FakeSockets: SocketFactory, @unchecked Sendable {
    let socket = FakeSocket()
    private let lock = NSLock()
    private var _listener: SocketListener?
    var listener: SocketListener? { lock.withLock { _listener } }
    var url = ""
    func open(_ url: String, _ listener: SocketListener) -> Socket {
        lock.withLock { self.url = url; _listener = listener }
        return socket
    }
}

final class RealtimeTests: XCTestCase {
    func testJoinsWithFiltersAndPassesTheTable() async throws {
        let s = ServerClient(url: "https://x.test", key: "K",
                             store: MemorySessionStore(Session(accessToken: "TOK", refreshToken: "R", expiresAt: 99_999, userId: "u1", anonymous: true)),
                             transport: FakeTransport { _ in HTTPResponse(200, "{}") }, clock: { 1000 })
        let sockets = FakeSockets()
        let rt = Realtime(server: s, sockets: sockets)
        let got = Counter()
        let table = TableBox()
        await rt.join("group:g1", [.init("meetups", filter: "group_id=eq.g1"), .init("meetups", event: "DELETE")],
                      onChange: { t in table.set(t); got.add() })
        XCTAssertEqual(sockets.url, "wss://x.test/realtime/v1/websocket?apikey=K&vsn=1.0.0")
        sockets.listener!.onOpen()
        try await Task.sleep(nanoseconds: 100_000_000)
        let join = JSON.parse(sockets.socket.out.first ?? "")!
        XCTAssertEqual(join.string("topic"), "realtime:group:g1")
        XCTAssertEqual(join.string("event"), "phx_join")
        XCTAssertEqual(join["payload"]?.string("access_token"), "TOK")
        let changes = join["payload"]?["config"]?["postgres_changes"]?.array ?? []
        XCTAssertEqual(changes.count, 2)
        XCTAssertEqual(changes[0].string("filter"), "group_id=eq.g1")
        sockets.listener!.onMessage(#"{"topic":"realtime:group:g1","event":"postgres_changes","payload":{"data":{"table":"meetups","type":"INSERT"}}}"#)
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(got.value, 1)
        XCTAssertEqual(table.value, "meetups")
    }
}

final class TableBox: @unchecked Sendable {
    private let lock = NSLock()
    private var v = ""
    func set(_ s: String) { lock.withLock { v = s } }
    var value: String { lock.withLock { v } }
}

@MainActor
final class FakeGroupAPITests: XCTestCase {
    func testServerRulesTheScreensSee() async throws {
        let api = FakeGroupAPI(seed: "member", delayMs: 0)
        XCTAssertEqual(api.me()?.registered, false)
        do { _ = try await api.createGroup(name: "x", myName: "y", startsOn: nil, endsOn: nil, myTrip: nil); XCTFail() } catch let e as ApiError { XCTAssertEqual(e.code, "must_register") }
        let g = try await api.group("g1")
        XCTAssertFalse(g.admin)
        XCTAssertTrue(g.requests.isEmpty, "only admins see requests")
        let admin = FakeGroupAPI(seed: "admin", delayMs: 0)
        do { try await admin.setRole("g1", userId: "u1", role: .admin); XCTFail() } catch let e as ApiError { XCTAssertEqual(e.code, "admin_must_register") }
        let p = try await FakeGroupAPI(seed: "admin", delayMs: 0).preview("kzb-qrm")
        XCTAssertEqual(p.status, .ok)
        XCTAssertTrue(p.alreadyMember)
    }
}
