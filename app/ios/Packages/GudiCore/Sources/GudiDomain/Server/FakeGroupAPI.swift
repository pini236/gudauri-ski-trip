import Foundation

/// A pretend server for the screenshots (a launch argument only; it never meets Apple's or Google's sign-in sheet or
/// the real server), as Android's debug DevServer. It follows the server's rules where the screens can see them
/// (guests cannot create groups, admin actions for admins only, the last admin cannot step down, join statuses).
/// Made-up people and flights: never the crew's (decision 27).
@MainActor
public final class FakeGroupAPI: GroupAPI {
    private var meNow: Me?
    private var groups: [(String, TripGroup)] = []
    private var trips: [String: Trip] = [:]
    private var mine: [String: Int] = [:]
    private var watches: [String: FakeWatch] = [:]
    private var mineListeners: [UUID: @MainActor ([String]) -> Void] = [:]
    /// How long each call takes (0 in the tests).
    private let delay: UInt64

    public init(seed: String, delayMs: UInt64 = 120) {
        delay = delayMs * 1_000_000
        self.seed(seed)
    }

    private func id() -> String { UUID().uuidString.lowercased() }
    private func wait() async { if delay > 0 { try? await Task.sleep(nanoseconds: delay) } }
    private func meOrThrow() throws -> Me { guard let m = meNow else { throw ApiError("not_signed_in") }; return m }
    private func get(_ gid: String) -> TripGroup? { groups.first { $0.0 == gid }?.1 }
    private func put(_ g: TripGroup) {
        if let i = groups.firstIndex(where: { $0.0 == g.id }) { groups[i].1 = g } else { groups.append((g.id, g)) }
        watches[g.id]?.push()
    }
    private func drop(_ gid: String) { groups.removeAll { $0.0 == gid }; watches[gid]?.push() }

    private static let flight = Trip(
        Leg(Day(2027, 1, 10)!, flight: "GD 101", from: "TLV · Tel Aviv", to: "TBS · Tbilisi", departs: ClockTime(16, 0), arrives: ClockTime(20, 35)),
        Leg(Day(2027, 1, 15)!, flight: "GD 102", from: "TBS · Tbilisi", to: "TLV · Tel Aviv", departs: ClockTime(1, 35), arrives: ClockTime(2, 15)))
    private static let flight2 = Trip(
        Leg(Day(2027, 1, 11)!, flight: "XY 455", from: "TLV · Tel Aviv", to: "KUT · Kutaisi", departs: ClockTime(6, 10), arrives: ClockTime(9, 5)),
        Leg(Day(2027, 1, 15)!, flight: "XY 456", from: "KUT · Kutaisi", to: "TLV · Tel Aviv", departs: ClockTime(10, 0), arrives: ClockTime(12, 50)))

    /// The screenshots' starting points: none (nobody, no group), invited (a group with its invite, nobody on this phone
    /// yet), member (a guest in the group, without a flight), admin (a registered admin, with a request), two (the
    /// admin, also in a second group: "my groups", K-4).
    public func seed(_ kind: String) {
        groups = []; trips = [:]; mine = [:]; meNow = nil
        if kind == "none" { return }
        let admin = kind == "admin" || kind == "two"
        let he = Strings.shared.lang == .he
        let names = he ? ["נועה כהן", "איתי לוי", "דנה מזרחי", "עומר פרץ", "תמר אברהם", "יואב שחר"]
                       : ["Noa Cohen", "Itai Levi", "Dana Mizrahi", "Omer Peretz", "Tamar Avraham", "Yoav Shahar"]
        let me = Me(userId: "me", registered: admin, name: admin ? names[0] : names[1], google: admin)
        meNow = me
        let t1 = id(); trips[t1] = Self.flight
        let t2 = id(); trips[t2] = Self.flight2
        let members = names.enumerated().map { i, n -> Member in
            let isMe = n == me.name
            let hasTrip = i < 4 && !(isMe && !admin)
            return Member(userId: isMe ? "me" : "u\(i)", name: n, role: i == 0 || i == 2 ? .admin : .member, me: isMe,
                          trip: hasTrip ? (i % 2 == 0 ? Self.flight : Self.flight2) : nil, tripId: hasTrip ? (i % 2 == 0 ? t1 : t2) : nil,
                          enteredByAdmin: i == 3 ? names[0] : nil, registered: i == 0 || i == 2)
        }
        let gudauri = TimeZone(secondsFromGMT: 4 * 3600)!
        func at(_ d: Int, _ h: Int, _ m: Int) -> Date {
            var c = DateComponents(); c.year = 2027; c.month = 1; c.day = d; c.hour = h; c.minute = m
            var cal = Calendar(identifier: .gregorian); cal.timeZone = gudauri
            return cal.date(from: c)!
        }
        let g = TripGroup(id: "g1", name: he ? "גודאורי 2027" : "Gudauri 2027", startsOn: Day(2027, 1, 10), endsOn: Day(2027, 1, 15), members: members,
                      invite: Invite(id: "i1", code: "KZBQRM", token: String(repeating: "t", count: 40), requiresApproval: false),
                      requests: admin ? [JoinRequest(id: "r1", userId: "u9", name: names[5], reclaim: true)] : [],
                      meetups: [GroupMeetup(id: "m1", station: "158744075b", at: at(11, 9, 30), byName: names[0]),
                                GroupMeetup(id: "m2", station: "158744055b", at: at(11, 13, 0), byName: names[2])])
        if kind == "two" {
            put(TripGroup(id: "g2", name: he ? "סקי עם המשפחה" : "Family ski", startsOn: Day(2027, 2, 14), endsOn: Day(2027, 2, 18),
                      members: [Member(userId: "me", name: me.name ?? "", role: .member, me: true, registered: true),
                                Member(userId: "u7", name: he ? "רוני כהן" : "Roni Cohen", role: .admin, me: false, registered: true),
                                Member(userId: "u8", name: he ? "גלי כהן" : "Gali Cohen", role: .member, me: false)],
                      invite: Invite(id: "i2", code: "PMWHTD", token: String(repeating: "u", count: 40), requiresApproval: false), requests: [], meetups: []))
        }
        put(g)
        if kind == "invited" { var x = g; x.members.removeAll { $0.userId == "me" }; put(x); meNow = nil }
    }

    public func me() -> Me? { meNow }

    public func signInGuest() async throws -> Me {
        await wait()
        if let m = meNow { return m }
        let m = Me(userId: id(), registered: false, name: nil); meNow = m; return m
    }

    public func signIn(provider: String, idToken: String, nonce: String?) async throws -> Me {
        await wait()
        var m = meNow ?? Me(userId: id(), registered: false, name: nil)
        m.registered = true
        if provider == "apple" { m.apple = true } else { m.google = true }
        meNow = m
        return m
    }

    public func signOut() async throws { meNow = nil }

    public func deleteAccount() async throws {
        await wait()
        let me = try meOrThrow()
        for (gid, _) in groups { remove(gid, me.userId) }
        meNow = nil
    }

    public func rename(_ name: String) async throws { await wait(); var m = try meOrThrow(); m.name = name.trimmingCharacters(in: .whitespaces); meNow = m }

    public func myGroups() async throws -> [GroupSummary] {
        await wait()
        guard let me = meNow else { return [] }
        return groups.map(\.1).filter { $0.members.contains { $0.userId == me.userId } }
            .map { GroupSummary(id: $0.id, name: $0.name, startsOn: $0.startsOn, endsOn: $0.endsOn) }.byStart()
    }

    func view(_ id: String) -> TripGroup? {
        guard let me = meNow, var g = get(id), let mine = g.members.first(where: { $0.userId == me.userId }) else { return nil }
        g.members = g.members.map { var m = $0; m.me = m.userId == me.userId; return m }
        if mine.role != .admin { g.requests = [] }
        return g
    }

    public func group(_ id: String) async throws -> TripGroup {
        await wait()
        _ = try meOrThrow()
        guard get(id) != nil else { throw ApiError("not_found") }
        guard let g = view(id) else { throw ApiError("not_member") }
        return g
    }

    public func watch(_ id: String) -> GroupWatch {
        if let w = watches[id] { return w }
        let w = FakeWatch(id, api: self)
        watches[id] = w
        return w
    }

    public func createGroup(name: String, myName: String, startsOn: Day?, endsOn: Day?, myTrip: Trip?) async throws -> String {
        await wait()
        var me = try meOrThrow()
        guard me.registered else { throw ApiError("must_register") }
        let gid = id()
        let tid = myTrip.map { t -> String in let k = id(); trips[k] = t; return k }
        put(TripGroup(id: gid, name: name.trimmingCharacters(in: .whitespaces), startsOn: startsOn, endsOn: endsOn,
                  members: [Member(userId: me.userId, name: myName.trimmingCharacters(in: .whitespaces), role: .admin, me: true, trip: myTrip, tripId: tid, registered: true)],
                  invite: Invite(id: id(), code: randomCode(), token: id() + id(), requiresApproval: false), requests: [], meetups: []))
        me.name = myName.trimmingCharacters(in: .whitespaces); meNow = me
        return gid
    }

    private func randomCode() -> String { String((0..<6).map { _ in InviteCode.letters.randomElement()! }) }

    private func admin(_ gid: String) throws -> TripGroup {
        let me = try meOrThrow()
        guard let g = get(gid) else { throw ApiError("not_found") }
        guard g.members.contains(where: { $0.userId == me.userId && $0.role == .admin }) else { throw ApiError("not_admin") }
        return g
    }

    public func updateGroup(_ id: String, name: String, startsOn: Day?, endsOn: Day?) async throws {
        await wait()
        var g = try admin(id); g.name = name.trimmingCharacters(in: .whitespaces); g.startsOn = startsOn; g.endsOn = endsOn; put(g)
    }

    public func deleteGroup(_ id: String) async throws { await wait(); _ = try admin(id); drop(id) }

    public func newInvite(_ groupId: String, requiresApproval: Bool) async throws -> Invite {
        await wait()
        var g = try admin(groupId)
        let inv = Invite(id: id(), code: randomCode(), token: id() + id(), requiresApproval: requiresApproval)
        g.invite = inv; put(g)
        return inv
    }

    private func byCode(_ code: String) -> TripGroup? {
        let c = Groups.normalizeCode(code)
        return groups.map(\.1).first { $0.invite?.code == c.uppercased() || $0.invite?.token == c }
    }

    public func preview(_ code: String) async throws -> InvitePreview {
        await wait()
        let me = try meOrThrow()
        guard let g = byCode(code) else { return InvitePreview(status: .invalid_code) }
        let member = g.members.contains { $0.userId == me.userId }
        let approval = g.invite?.requiresApproval == true
        // like the server: an invite that needs approval shows no names to who is not in the group
        return InvitePreview(status: .ok, groupId: g.id, name: g.name, startsOn: g.startsOn, endsOn: g.endsOn, requiresApproval: approval,
                       alreadyMember: member, members: approval && !member ? [] : g.members.map { ($0.userId, $0.name) })
    }

    public func join(_ code: String, myName: String) async throws -> JoinResult {
        await wait()
        var me = try meOrThrow()
        guard var g = byCode(code) else { return JoinResult(.invalid_code) }
        if g.members.contains(where: { $0.userId == me.userId }) { return JoinResult(.already_member, g.id) }
        if g.invite?.requiresApproval == true {
            g.requests.append(JoinRequest(id: id(), userId: me.userId, name: myName, reclaim: false)); put(g)
            return JoinResult(.pending, g.id)
        }
        g.members.append(Member(userId: me.userId, name: myName.trimmingCharacters(in: .whitespaces), role: .member, me: true, registered: me.registered))
        put(g)
        if me.name == nil { me.name = myName.trimmingCharacters(in: .whitespaces); meNow = me }
        return JoinResult(.joined, g.id)
    }

    public func reclaim(_ code: String, memberId: String) async throws -> JoinResult {
        await wait()
        let me = try meOrThrow()
        guard var g = byCode(code) else { return JoinResult(.invalid_code) }
        guard let m = g.members.first(where: { $0.userId == memberId }) else { return JoinResult(.no_such_member) }
        if m.registered { return JoinResult(.sign_in_instead) }
        g.requests.append(JoinRequest(id: id(), userId: me.userId, name: m.name, reclaim: true)); put(g)
        return JoinResult(.pending, g.id)
    }

    public func decide(_ requestId: String, approve: Bool) async throws {
        await wait()
        guard var g = groups.map(\.1).first(where: { $0.requests.contains { $0.id == requestId } }) else { throw ApiError("request_closed") }
        _ = try admin(g.id)
        let r = g.requests.first { $0.id == requestId }!
        if approve && !r.reclaim { g.members.append(Member(userId: r.userId, name: r.name, role: .member, me: false)) }
        g.requests.removeAll { $0.id == requestId }
        put(g)
    }

    private func remove(_ gid: String, _ user: String) {
        guard var g = get(gid) else { return }
        g.members.removeAll { $0.userId == user }
        if g.members.isEmpty { drop(gid); return }
        // the last admin leaves: the oldest registered member takes over
        if !g.members.contains(where: { $0.role == .admin }), let i = g.members.firstIndex(where: \.registered) { g.members[i].role = .admin }
        put(g)
    }

    public func leave(_ groupId: String) async throws { await wait(); remove(groupId, try meOrThrow().userId) }
    public func removeMember(_ groupId: String, userId: String) async throws { await wait(); _ = try admin(groupId); remove(groupId, userId) }

    public func setRole(_ groupId: String, userId: String, role: MemberRole) async throws {
        await wait()
        var g = try admin(groupId)
        guard let i = g.members.firstIndex(where: { $0.userId == userId }) else { throw ApiError("not_member") }
        if role == .admin && !g.members[i].registered { throw ApiError("admin_must_register") }
        if role == .member && !g.members.contains(where: { $0.role == .admin && $0.userId != userId }) { throw ApiError("last_admin") }
        g.members[i].role = role
        put(g)
    }

    public func claimAdmin(_ groupId: String) async throws {
        await wait()
        let me = try meOrThrow()
        guard var g = get(groupId) else { throw ApiError("not_found") }
        guard me.registered else { throw ApiError("must_register") }
        if g.members.contains(where: { $0.role == .admin }) { throw ApiError("group_has_admin") }
        if let i = g.members.firstIndex(where: { $0.userId == me.userId }) { g.members[i].role = .admin }
        put(g)
    }

    public func revokeInvite(_ inviteId: String) async throws {
        await wait()
        guard var g = groups.map(\.1).first(where: { $0.invite?.id == inviteId }) else { throw ApiError("not_found") }
        _ = try admin(g.id)
        g.invite = nil; put(g)
    }

    public func cancelRequest(_ groupId: String) async throws {
        await wait()
        let me = try meOrThrow()
        guard var g = get(groupId) else { return }
        g.requests.removeAll { $0.userId == me.userId }; put(g)
    }

    public func pendingRequests() async throws -> [String] {
        guard let me = meNow else { return [] }
        return groups.map(\.1).filter { $0.requests.contains { $0.userId == me.userId } }.map(\.id)
    }

    public func watchMine(_ onChange: @escaping @MainActor ([String]) -> Void) -> () -> Void {
        let k = UUID()
        mineListeners[k] = onChange
        return { [weak self] in Task { @MainActor in self?.mineListeners[k] = nil } }
    }

    public func showMyTrip(_ groupId: String, myName: String, trip: Trip?) async throws {
        await wait()
        let me = try meOrThrow()
        guard var g = get(groupId) else { throw ApiError("not_found") }
        let tid = trip.map { t -> String in let k = id(); trips[k] = t; return k }
        g.members = g.members.map { m in
            guard m.userId == me.userId else { return m }
            var m = m; m.name = myName.trimmingCharacters(in: .whitespaces); m.trip = trip; m.tripId = tid; m.enteredByAdmin = nil; return m
        }
        put(g)
    }

    public func sameFlight(_ groupId: String, tripId: String) async throws -> Trip {
        await wait()
        let me = try meOrThrow()
        guard var g = get(groupId) else { throw ApiError("not_found") }
        guard let t = trips[tripId] else { throw ApiError("trip_not_in_group") }
        let k = id(); trips[k] = t
        g.members = g.members.map { m in
            guard m.userId == me.userId else { return m }
            var m = m; m.trip = t; m.tripId = k; m.enteredByAdmin = nil; return m
        }
        put(g)
        return t
    }

    public func myTripOnServer() async throws -> Trip? { nil }

    public func setMemberTrip(_ groupId: String, userId: String, trip: Trip) async throws {
        await wait()
        var g = try admin(groupId)
        let me = try meOrThrow()
        let by = g.members.first { $0.userId == me.userId }?.name
        let k = id(); trips[k] = trip
        g.members = g.members.map { m in
            guard m.userId == userId else { return m }
            var m = m; m.trip = trip; m.tripId = k; m.enteredByAdmin = by; return m
        }
        put(g)
    }

    public func leaderboard(_ groupId: String, game: String) async throws -> [GroupScore] {
        await wait()
        let me = try meOrThrow()
        guard let g = get(groupId) else { throw ApiError("not_found") }
        let best = [12480, 11920, 9310, 8775, 6040]
        return g.members.prefix(5).enumerated().map { i, m in
            GroupScore(userId: m.userId, name: m.name, best: m.userId == me.userId ? (mine[game] ?? best[i]) : best[i], me: m.userId == me.userId)
        }.sorted { $0.best > $1.best }
    }

    public func submitBest(game: String, score: Int) async -> Bool {
        guard let me = meNow, groups.contains(where: { $0.1.members.contains { $0.userId == me.userId } }) else { return false }
        mine[game] = max(mine[game] ?? 0, score)
        return true
    }

    public func addMeetup(_ groupId: String, station: String, at: Date) async throws {
        await wait()
        let me = try meOrThrow()
        guard var g = get(groupId) else { throw ApiError("not_found") }
        g.meetups.append(GroupMeetup(id: id(), station: station, at: at, byName: g.members.first { $0.userId == me.userId }?.name))
        g.meetups.sort { $0.at < $1.at }
        put(g)
    }

    public func removeMeetup(_ groupId: String, meetupId: String) async throws {
        await wait()
        guard var g = get(groupId) else { throw ApiError("not_found") }
        g.meetups.removeAll { $0.id == meetupId }; put(g)
    }

    public func tripSaved(_ trip: Trip?) async {}
}

@MainActor
final class FakeWatch: GroupWatch {
    private let id: String
    private weak var api: FakeGroupAPI?
    private var conts: [UUID: AsyncStream<Shown>.Continuation] = [:]
    private(set) var now = Shown(group: nil)

    init(_ id: String, api: FakeGroupAPI) { self.id = id; self.api = api; push() }

    func push() {
        let g = api?.view(id)
        now = g == nil && now.group != nil ? Shown(group: nil, readAt: now.readAt, gone: true) : Shown(group: g, readAt: Int64(Date().timeIntervalSince1970 * 1000))
        for c in conts.values { c.yield(now) }
    }

    func states() -> AsyncStream<Shown> {
        let (s, c) = AsyncStream<Shown>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let k = UUID()
        conts[k] = c
        c.yield(now)
        c.onTermination = { [weak self] _ in Task { @MainActor in self?.conts[k] = nil } }
        return s
    }

    func open() async { push() }
    func close() async {}
    func refresh() async { push() }
    func clearRefused() async {}
}
