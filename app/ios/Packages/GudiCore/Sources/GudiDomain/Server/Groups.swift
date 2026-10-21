import Foundation

/// The group, as the server sees it (docs/USERS.md, screens Q1 to Q10): typed calls over [ServerClient], one per action
/// of the server's code and the reads the group page needs, as Android's server/Groups.kt. Every call may throw
/// [Offline] or [ServerError] (its code is the server's: "not_admin", "last_admin"...).
public struct Groups: Sendable {
    public let server: ServerClient
    public init(_ server: ServerClient) { self.server = server }

    public struct Member: Sendable, Equatable {
        public var userId: String, name: String, role: String, tripId: String?
        public init(_ userId: String, _ name: String, role: String = "member", tripId: String? = nil) {
            self.userId = userId; self.name = name; self.role = role; self.tripId = tripId
        }
        public var admin: Bool { role == "admin" }
    }

    public struct Group: Sendable, Equatable {
        public var id: String, name: String, startsOn: Day?, endsOn: Day?
        public init(_ id: String, _ name: String, _ startsOn: Day?, _ endsOn: Day?) {
            self.id = id; self.name = name; self.startsOn = startsOn; self.endsOn = endsOn
        }
    }

    /// A working invite (Q3, Q4): the six letters to say aloud, and the token for the link.
    public struct Invite: Sendable, Equatable {
        public var id: String, code: String, token: String, requiresApproval: Bool, maxUses: Int?, expiresAt: String?
        /// How many joined with it; at [maxUses] it stops working.
        public var uses: Int = 0
        /// When it stops working: [expiresAt], or else the day after the trip ends, or else 90 days after it was made.
        public var validUntil: Date?
        public init(id: String, code: String, token: String, requiresApproval: Bool, maxUses: Int?, expiresAt: String?, uses: Int = 0, validUntil: Date? = nil) {
            self.id = id; self.code = code; self.token = token; self.requiresApproval = requiresApproval; self.maxUses = maxUses
            self.expiresAt = expiresAt; self.uses = uses; self.validUntil = validUntil
        }
        /// Still lets people in (not used up, not expired); a revoked one is never returned.
        public func working(_ now: Date = Date()) -> Bool {
            (maxUses == nil || uses < maxUses!) && (validUntil == nil || now < validUntil!)
        }
    }

    /// What an invite opens (Q5): the group and who is in it. [status] is "ok" or why not ("invalid_code"...).
    public struct Preview: Sendable, Equatable {
        public var status: String
        public var group: Group? = nil
        public var requiresApproval = false
        public var alreadyMember = false
        public var members: [Member] = []
    }

    /// "joined", "pending", "already_member", or why not ("invalid_code", "rate_limited", "group_full",
    /// "no_such_member", "sign_in_instead").
    public struct Joined: Sendable, Equatable { public var status: String; public var groupId: String? }

    /// A request waiting for an admin (Q10): to join, or to be a member again ("reclaim").
    public struct JoinRequest: Sendable, Equatable {
        public var id, groupId, userId, name, kind: String
        public var reclaimUserId: String?
        public var status: String
    }

    /// [createdBy]: who set it (nil once that person deleted their account, or while it waits to be sent).
    public struct Meetup: Sendable, Equatable {
        public var id, groupId, station: String
        public var at: Date
        public var note: String?
        public var createdBy: String?
        public init(id: String, groupId: String, station: String, at: Date, note: String? = nil, createdBy: String? = nil) {
            self.id = id; self.groupId = groupId; self.station = station; self.at = at; self.note = note; self.createdBy = createdBy
        }
    }

    public struct Score: Sendable, Equatable {
        public var userId, name: String
        public var best: Int
        public init(_ userId: String, _ name: String, _ best: Int) { self.userId = userId; self.name = name; self.best = best }
    }

    /// A trip shown in the group, and who entered it: an admin for a member (Q7, "entered by") or its owner.
    public struct Flight: Sendable, Equatable {
        public var id, ownerId: String
        public var enteredBy: String?
        public var trip: Trip
        public var byAdmin: Bool { enteredBy != nil && enteredBy != ownerId }
    }

    // MARK: - Joining (Q4 to Q6)

    /// A code is six letters (Q4) or a link's token; a whole link works too. Counts as an attempt.
    public func preview(_ code: String) async throws -> Preview {
        let o = try await server.call("invite_preview", .obj(["code": .string(Self.normalizeCode(code))]))
        let status = o.string("status")
        guard status == "ok" else { return Preview(status: status) }
        let members = o.objects("members").map { Member($0.string("user_id"), $0.string("display_name")) }
        return Preview(status: status, group: Self.group(o, idKey: "group_id"), requiresApproval: o.bool("requires_approval"),
                       alreadyMember: o.bool("already_member"), members: members)
    }

    public func join(_ code: String, displayName: String) async throws -> Joined {
        Self.joined(try await server.call("join_group", .obj(["code": .string(Self.normalizeCode(code)),
                                                              "display_name": .string(displayName.trimmingCharacters(in: .whitespaces))])))
    }

    /// "I'm already in the group" (Q6): be [memberId] again; an admin approves.
    public func reclaim(_ code: String, memberId: String) async throws -> Joined {
        Self.joined(try await server.call("request_reclaim", .obj(["code": .string(Self.normalizeCode(code)), "member_id": .string(memberId)])))
    }

    public func cancelRequest(_ requestId: String) async throws {
        _ = try await server.call("cancel_join_request", .obj(["request_id": .string(requestId)]))
    }

    /// My requests still waiting (so the app can show "waiting for approval" after a restart).
    public func myRequests() async throws -> [JoinRequest] {
        guard let me = try await server.current()?.userId else { return [] }
        return try await server.select("join_requests", "select=*&status=eq.pending&user_id=eq.\(ServerClient.enc(me))").map(Self.request)
    }

    // MARK: - My groups

    /// Registered only; I am its admin, with a first invite ready.
    public func create(name: String, displayName: String, startsOn: Day?, endsOn: Day?, tripId: String?) async throws -> String {
        let o = try await server.call("create_group", .obj([
            "name": .string(name.trimmingCharacters(in: .whitespaces)), "display_name": .string(displayName.trimmingCharacters(in: .whitespaces)),
            "starts_on": .str(startsOn?.iso), "ends_on": .str(endsOn?.iso), "trip_id": .str(tripId)]))
        return o.string("group_id")
    }

    /// The groups I am in. Reading needs a session: without one there are none.
    public func mine() async throws -> [Group] {
        guard let me = try await server.current()?.userId else { return [] }
        return try await server.select("group_members", "select=groups(id,name,starts_on,ends_on)&user_id=eq.\(ServerClient.enc(me))")
            .compactMap { $0["groups"] }.filter { $0.object != nil }.map { Self.group($0, idKey: "id") }
    }

    /// My name in the group and which of my trips it shows (nil: none).
    public func setMine(_ groupId: String, displayName: String, tripId: String?) async throws {
        _ = try await server.call("set_my_membership", .obj(["group_id": .string(groupId),
                                                            "display_name": .string(displayName.trimmingCharacters(in: .whitespaces)), "trip_id": .str(tripId)]))
    }

    /// "I'm on the same flight" (Q7): that member's flight becomes mine (decision 27: one trip a person). My trip's id.
    public func sameFlight(_ groupId: String, tripId: String, myTripId: String?) async throws -> String {
        var b: [String: JSON] = ["group_id": .string(groupId), "trip_id": .string(tripId)]
        if let myTripId { b["my_trip_id"] = .string(myTripId) }
        return try await server.call("same_flight", .object(b)).string("trip_id")
    }

    public func leave(_ groupId: String) async throws { _ = try await server.call("leave_group", .obj(["group_id": .string(groupId)])) }

    /// A registered member of a group left without an admin takes it over.
    public func claimAdmin(_ groupId: String) async throws { _ = try await server.call("claim_admin", .obj(["group_id": .string(groupId)])) }

    // MARK: - The group page (Q7 to Q9)

    public func head(_ groupId: String) async throws -> Group? {
        try await server.select("groups", "select=id,name,starts_on,ends_on&id=eq.\(ServerClient.enc(groupId))").first.map { Self.group($0, idKey: "id") }
    }

    public func members(_ groupId: String) async throws -> [Member] {
        try await server.select("group_members", "select=user_id,display_name,role,trip_id&group_id=eq.\(ServerClient.enc(groupId))&order=joined_at,user_id")
            .map { Member($0.string("user_id"), $0.string("display_name"), role: $0.string("role"), tripId: $0.str("trip_id")) }
    }

    public func flightDetails(_ groupId: String) async throws -> [String: Flight] {
        try await flights(ids: try await members(groupId).compactMap(\.tripId))
    }

    public func flights(ids: [String]) async throws -> [String: Flight] {
        guard !ids.isEmpty else { return [:] }
        let rows = try await server.select("trips", "select=*&id=in.(\(ids.map(ServerClient.enc).joined(separator: ",")))")
        var out: [String: Flight] = [:]
        for o in rows {
            guard let t = TripRow.trip(o) else { continue }
            out[o.string("id")] = Flight(id: o.string("id"), ownerId: o.string("owner_id"), enteredBy: o.str("entered_by"), trip: t)
        }
        return out
    }

    public func meetups(_ groupId: String) async throws -> [Meetup] {
        try await server.select("meetups", "select=id,group_id,station,meet_at,note,created_by&group_id=eq.\(ServerClient.enc(groupId))&order=meet_at")
            .compactMap(Self.meetup)
    }

    /// Any member may add, change or remove a meetup (Q8); the last one saved wins.
    public func addMeetup(_ groupId: String, station: String, at: Date, note: String? = nil, id: String? = nil) async throws -> Meetup {
        var row: [String: JSON] = ["group_id": .string(groupId), "station": .string(station), "meet_at": .string(ISOTime.text(at)), "note": .str(note)]
        // an id from the phone (the queue's own): a meetup made without signal and sent twice is still one meetup
        if let id { row["id"] = .string(id) }
        var made: JSON?
        if let id {
            made = try await server.insertOnce("meetups", .object(row))
            if made == nil { made = try await server.select("meetups", "select=id,group_id,station,meet_at,note,created_by&id=eq.\(ServerClient.enc(id))").first }
        } else {
            made = try await server.insert("meetups", .object(row))
        }
        guard let m = made.flatMap(Self.meetup) else { throw ServerError("conflict", 409) }
        return m
    }

    public func changeMeetup(_ id: String, station: String, at: Date, note: String? = nil) async throws -> Meetup? {
        try await server.update("meetups", id: id, .obj(["station": .string(station), "meet_at": .string(ISOTime.text(at)), "note": .str(note)])).flatMap(Self.meetup)
    }

    public func removeMeetup(_ id: String) async throws { try await server.delete("meetups", id: id) }

    /// My best is kept only if higher, so sending twice (after no signal) does no harm. The best kept.
    public func submitScore(game: String, score: Int) async throws -> Int {
        try await server.call("submit_score", .obj(["game": .string(game), "score": .int(score)])).int("best") ?? score
    }

    /// The group's table for one game (Q9), best first.
    public func leaderboard(_ groupId: String, game: String) async throws -> [Score] {
        try await server.callList("group_leaderboard", .obj(["group_id": .string(groupId), "game": .string(game)]))
            .map { Score($0.string("user_id"), $0.string("display_name"), $0.int("best") ?? 0) }
    }

    // MARK: - Admins (Q3, Q10)

    public func update(_ groupId: String, name: String, startsOn: Day?, endsOn: Day?) async throws {
        _ = try await server.call("update_group", .obj(["group_id": .string(groupId), "name": .string(name.trimmingCharacters(in: .whitespaces)),
                                                       "starts_on": .str(startsOn?.iso), "ends_on": .str(endsOn?.iso)]))
    }

    public func delete(_ groupId: String) async throws { _ = try await server.call("delete_group", .obj(["group_id": .string(groupId)])) }

    /// The working invite, or nil (every member sees it). One used up or expired is not returned.
    public func invite(_ groupId: String) async throws -> Invite? {
        let rows = try await server.select("invites", "select=*,groups(ends_on)&group_id=eq.\(ServerClient.enc(groupId))&revoked_at=is.null&order=created_at.desc&limit=5")
        let now = Date(timeIntervalSince1970: TimeInterval(server.nowSeconds()))
        return rows.map(Self.invite).first { $0.working(now) }
    }

    /// A new invite that replaces the working one (a leaked code stops working).
    public func newInvite(_ groupId: String, requiresApproval: Bool = false, maxUses: Int? = nil, expiresAt: Date? = nil) async throws -> Invite {
        Self.invite(try await server.call("create_invite", .obj(["group_id": .string(groupId), "requires_approval": .bool(requiresApproval),
                                                                "max_uses": .int(maxUses), "expires_at": .str(expiresAt.map(ISOTime.text))])))
    }

    public func revokeInvite(_ inviteId: String) async throws { _ = try await server.call("revoke_invite", .obj(["invite_id": .string(inviteId)])) }

    public func requests(_ groupId: String) async throws -> [JoinRequest] {
        try await server.select("join_requests", "select=*&status=eq.pending&group_id=eq.\(ServerClient.enc(groupId))&order=created_at").map(Self.request)
    }

    public func decide(_ requestId: String, approve: Bool) async throws {
        _ = try await server.call("decide_join_request", .obj(["request_id": .string(requestId), "approve": .bool(approve)]))
    }

    public func remove(_ groupId: String, userId: String) async throws {
        _ = try await server.call("remove_member", .obj(["group_id": .string(groupId), "user_id": .string(userId)]))
    }

    /// "admin" or "member"; at least one admin stays, and admins are registered.
    public func setRole(_ groupId: String, userId: String, role: String) async throws {
        _ = try await server.call("set_member_role", .obj(["group_id": .string(groupId), "user_id": .string(userId), "role": .string(role)]))
    }

    /// An admin fills in a member's flight (Q7, "entered by"); not over a trip the member keeps themselves.
    public func setMemberTrip(_ groupId: String, userId: String, trip: Trip) async throws -> String {
        try await server.call("set_member_trip", .obj(["group_id": .string(groupId), "user_id": .string(userId), "trip": TripRow.of(trip)])).string("trip_id")
    }

    // MARK: - Reading rows

    static func group(_ o: JSON, idKey: String) -> Group { Group(o.string(idKey), o.string("name"), date(o, "starts_on"), date(o, "ends_on")) }
    static func joined(_ o: JSON) -> Joined { Joined(status: o.string("status"), groupId: o.str("group_id")) }
    static func request(_ o: JSON) -> JoinRequest {
        JoinRequest(id: o.string("id"), groupId: o.string("group_id"), userId: o.string("user_id"), name: o.string("display_name"),
                    kind: o.string("kind"), reclaimUserId: o.str("reclaim_user_id"), status: o.string("status"))
    }
    static func meetup(_ o: JSON) -> Meetup? {
        guard let at = o.str("meet_at").flatMap(ISOTime.parse) else { return nil }
        return Meetup(id: o.string("id"), groupId: o.string("group_id"), station: o.string("station"), at: at, note: o.str("note"), createdBy: o.str("created_by"))
    }
    static func invite(_ o: JSON) -> Invite {
        // the server's rule (CONTRACT, "תוקף הזמנה"): expires_at, or the day after the trip, or 90 days
        var until: Date? = o.str("expires_at").flatMap(ISOTime.parse)
        if until == nil, let end = o["groups"].flatMap({ date($0, "ends_on") }) { until = Date(timeIntervalSince1970: TimeInterval(end.plus(days: 1).epochDay) * 86_400) }
        if until == nil, let made = o.str("created_at").flatMap(ISOTime.parse) { until = made.addingTimeInterval(90 * 86_400) }
        return Invite(id: o.string("id"), code: o.string("code"), token: o.string("token"), requiresApproval: o.bool("requires_approval"),
                      maxUses: o.int("max_uses"), expiresAt: o.str("expires_at"), uses: o.int("uses") ?? 0, validUntil: until)
    }

    public static func date(_ o: JSON, _ k: String) -> Day? {
        guard let s = o.str(k), s.count >= 10 else { return nil }
        return Day(iso: String(s.prefix(10)))
    }

    /// "abc def" or "ABC-DEF" -> "ABCDEF"; a whole link -> its token (the last part of the path, or "t=" in it); a
    /// token is kept as it is. As Android's Groups.normalizeCode.
    public static func normalizeCode(_ code: String) -> String {
        let letters = code.filter(\.isLetter)
        if letters.count == 6, code.allSatisfy({ $0.isLetter || $0 == " " || $0 == "-" }) { return letters.uppercased() }
        let t = code.trimmingCharacters(in: .whitespaces)
        guard t.contains("/") else { return t }
        if let r = t.range(of: #"[?&#]t=([A-Za-z0-9_-]+)"#, options: .regularExpression) {
            return String(t[r].split(separator: "=", maxSplits: 1)[1])
        }
        var path = Substring(t)
        if let q = path.firstIndex(where: { $0 == "?" || $0 == "#" }) { path = path[..<q] }
        while path.hasSuffix("/") { path = path.dropLast() }
        return String(path.split(separator: "/").last ?? "")
    }
}

/// "Your trip" as a row of the server's trips table, and back (Q7). The server keeps airport codes only (three
/// capitals): a place typed without a code is left out there and stays on the phone. As Android's TripRow.
public enum TripRow {
    public static func of(_ t: Trip) -> JSON {
        var o: [String: JSON] = [:]
        let dest = String(t.out.toCity.prefix(60))
        o["destination"] = dest.trimmingCharacters(in: .whitespaces).isEmpty ? .null : .string(dest)
        leg(&o, "out", t.out)
        if let r = t.ret { leg(&o, "ret", r) }
        o["ski_from"] = .str(t.ski?.lowerBound.iso)
        o["ski_to"] = .str(t.ski?.upperBound.iso)
        return .object(o)
    }

    private static func leg(_ o: inout [String: JSON], _ p: String, _ l: Leg) {
        o["\(p)_date"] = .string(l.date.iso)
        let f = String(l.flight.trimmingCharacters(in: .whitespaces).prefix(12))
        o["\(p)_flight"] = f.isEmpty ? .null : .string(f)
        o["\(p)_from"] = .str(l.fromCode)
        o["\(p)_to"] = .str(l.toCode)
        o["\(p)_departs"] = .str(l.departs?.text)
        o["\(p)_arrives"] = .str(l.arrives?.text)
    }

    /// A row from the server (another member's flight), or nil without an outbound date.
    public static func trip(_ o: JSON) -> Trip? {
        guard let out = leg(o, "out") else { return nil }
        let from = Groups.date(o, "ski_from"), to = Groups.date(o, "ski_to")
        return Trip(out, leg(o, "ret"), ski: from != nil && to != nil && from! <= to! ? from!...to! : nil)
    }

    private static func leg(_ o: JSON, _ p: String) -> Leg? {
        guard let d = Groups.date(o, "\(p)_date") else { return nil }
        func s(_ k: String) -> String { o["\(p)_\(k)"]?.isNull == false ? o.string("\(p)_\(k)") : "" }
        func time(_ k: String) -> ClockTime? { let v = s(k); return v.count >= 5 ? ClockTime(text: String(v.prefix(5))) : nil }
        return Leg(d, flight: s("flight"), from: s("from"), to: s("to"), departs: time("departs"), arrives: time("arrives"))
    }
}

/// My account on the server (A4), as Android's server/Account.kt.
public struct AccountService: Sendable {
    public let server: ServerClient
    public init(_ server: ServerClient) { self.server = server }

    public struct Me: Sendable, Equatable { public var id: String; public var name: String?; public var lang: String; public var anonymous: Bool }

    /// Nil when this phone never needed the server.
    public func me() async throws -> Me? {
        guard try await server.current() != nil else { return nil }
        let o = try await server.call("my_account")
        let n = o.str("display_name")
        return Me(id: o.string("id"), name: n == "null" ? nil : n, lang: o.str("lang") ?? "he", anonymous: o.bool("is_anonymous"))
    }

    /// My name and language on the server (the row rules allow only these two, only mine). The name reaches every group.
    public func setProfile(name: String?, lang: String) async throws {
        guard let me = try await server.current()?.userId else { return }
        let n = name?.trimmingCharacters(in: .whitespaces)
        _ = try await server.update("profiles", id: me, .obj(["display_name": .str(n?.isEmpty == false ? n : nil), "lang": .string(lang)]))
    }

    /// A guest whose Apple or Google account already exists: step 1, as the guest, a one-time ticket (15 minutes).
    public func mergeTicket() async throws -> String { try await server.call("create_merge_ticket").string("ticket") }

    public func mergeGuest(_ ticket: String) async throws { _ = try await server.call("merge_guest", .obj(["ticket": .string(ticket)])) }

    /// Required by both stores (App Store 5.1.1(v)): the account, its trips and memberships go; groups get a new admin.
    public func delete() async throws {
        guard try await server.current() != nil else { return }
        _ = try await server.call("delete_my_account")
        await server.forget()
    }
}
