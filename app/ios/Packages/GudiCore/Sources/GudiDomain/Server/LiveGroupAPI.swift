import Foundation

/// The screens' [GroupAPI] over the real server, as Android's group/LiveGroupApi.kt. A refusal becomes [ApiError] with
/// the server's code, no answer at all is "offline". My name and ways in are kept on the phone so [me] needs no
/// network. The group page reads through [sync] (the copy on the phone, Realtime and the queue of writes), so it opens at
/// once and keeps working with no signal.
@MainActor
public final class LiveGroupAPI: GroupAPI {
    private let server: ServerClient
    private let groups: Groups
    private let account: AccountService
    private let sync: Sync
    private let tripSync: TripSync
    private let lang: @MainActor () -> String
    private let profile: FileKV
    private var watches: [String: LiveWatch] = [:]

    public init(server: ServerClient, sync: Sync, tripSync: TripSync, profileFile: URL, lang: @escaping @MainActor () -> String) {
        self.server = server
        groups = Groups(server)
        account = AccountService(server)
        self.sync = sync
        self.tripSync = tripSync
        self.lang = lang
        profile = FileKV(profileFile)
    }

    private func io<T: Sendable>(_ block: () async throws -> T) async throws -> T {
        do {
            return try await block()
        } catch let e as ServerError {
            throw ApiError(e.code == "no_session" ? "not_signed_in" : e.code)
        } catch let e as ApiError {
            throw e
        } catch {
            throw ApiError("offline")
        }
    }

    private func remember(name: String?, provider: String?) {
        if let name { profile["name"] = name }
        if let provider { profile[provider] = "1" }
    }

    public func me() -> Me? {
        guard let s = server.stored() else { return nil }
        return Me(userId: s.userId, registered: !s.anonymous, name: profile["name"], google: profile["google"] == "1", apple: profile["apple"] == "1")
    }

    public func signInGuest() async throws -> Me {
        _ = try await io { try await server.currentOrGuest() }
        return me()!
    }

    public func signIn(provider: String, idToken: String, nonce: String?) async throws -> Me {
        let name: String? = try await io {
            do {
                _ = try await server.signInWithIdToken(provider: provider, idToken: idToken, nonce: nonce)
            } catch let e as ServerError where e.code == "identity_already_exists" {
                // that account already has an identity: bring the guest's groups and trips over to it
                let ticket = try await account.mergeTicket()
                _ = try await server.signInWithIdToken(provider: provider, idToken: idToken, nonce: nonce, link: false)
                try await account.mergeGuest(ticket)
            }
            return try await account.me()?.name
        }
        remember(name: name, provider: provider)
        return me()!
    }

    private func forgetAll() async {
        profile.removeAll()
        await sync.forget()
        await tripSync.forget()
        watches = [:]
    }

    public func signOut() async throws {
        await server.forget()
        await forgetAll()
    }

    public func deleteAccount() async throws {
        try await io { try await account.delete() }
        await forgetAll()
    }

    public func rename(_ name: String) async throws {
        let l = lang()
        try await io { try await account.setProfile(name: name, lang: l) }
        remember(name: name.trimmingCharacters(in: .whitespaces), provider: nil)
    }

    public func myGroups() async throws -> [GroupSummary] {
        guard server.stored() != nil else { return [] }
        let list: [Groups.Group]
        do {
            list = try await groups.mine()
            Task { await sync.refreshMine() }
        } catch let e as ServerError {
            throw ApiError(e.code == "no_session" ? "not_signed_in" : e.code)
        } catch {
            // no signal: the groups as the phone last saw them
            let kept = await sync.myGroups.groups
            if kept.isEmpty { throw ApiError("offline") }
            list = kept
        }
        return list.map { GroupSummary(id: $0.id, name: $0.name, startsOn: $0.startsOn, endsOn: $0.endsOn) }.byStart()
    }

    public func watch(_ id: String) -> GroupWatch {
        if let w = watches[id] { return w }
        let w = LiveWatch(id, sync: sync, me: { [weak self] in self?.server.stored()?.userId })
        watches[id] = w
        return w
    }

    /// The phone's copy of a group as the page shows it.
    nonisolated static func shown(_ s: Sync.Snapshot, me: String?) -> Shown {
        let names = Dictionary(s.members.map { ($0.userId, $0.name) }, uniquingKeysWith: { a, _ in a })
        var g: Group? = nil
        if let head = s.group, !s.members.isEmpty {
            g = Group(id: head.id, name: head.name, startsOn: head.startsOn, endsOn: head.endsOn,
                      members: s.members.map { m in
                          Member(userId: m.userId, name: m.name, role: m.admin ? .admin : .member, me: m.userId == me,
                                 trip: m.tripId.flatMap { s.flights[$0] }, tripId: m.tripId,
                                 enteredByAdmin: m.tripId.flatMap { s.enteredBy[$0] }.map { names[$0] ?? "" })
                      },
                      invite: s.invite.flatMap(working),
                      requests: s.requests.map { JoinRequest(id: $0.id, userId: $0.userId, name: $0.name, reclaim: $0.kind == "reclaim") },
                      meetups: s.meetups.map { Meetup(id: $0.id, station: $0.station, at: $0.at, note: $0.note, byName: $0.createdBy.flatMap { names[$0] }) })
        }
        return Shown(group: g, readAt: s.readAt, offline: s.offline, gone: s.gone, waiting: s.waiting > 0, refused: s.refused)
    }

    nonisolated static func working(_ i: Groups.Invite) -> Invite? {
        let exp = i.expiresAt.flatMap(ISOTime.parse)
        if let exp, exp <= Date() { return nil }
        return Invite(id: i.id, code: i.code, token: i.token, requiresApproval: i.requiresApproval, expiresAt: exp)
    }

    public func group(_ id: String) async throws -> Group {
        let me = server.stored()?.userId
        return try await io {
            guard let me else { throw ServerError("no_session", 401) }
            guard let head = try await groups.head(id) else { throw ServerError("not_found", 404) }
            let members = try await groups.members(id)
            let names = Dictionary(members.map { ($0.userId, $0.name) }, uniquingKeysWith: { a, _ in a })
            let trips = try await groups.flights(ids: members.compactMap(\.tripId))
            let admin = members.contains { $0.userId == me && $0.admin }
            let inv = (try? await groups.invite(id)).flatMap { $0 }.flatMap(Self.working)
            let reqs = admin ? try await groups.requests(id).map { JoinRequest(id: $0.id, userId: $0.userId, name: $0.name, reclaim: $0.kind == "reclaim") } : []
            let meets = try await groups.meetups(id).map { Meetup(id: $0.id, station: $0.station, at: $0.at, note: $0.note, byName: $0.createdBy.flatMap { names[$0] }) }
            return Group(id: id, name: head.name, startsOn: head.startsOn, endsOn: head.endsOn,
                         members: members.map { m in
                             let t = m.tripId.flatMap { trips[$0] }
                             let by = t.flatMap { $0.byAdmin ? $0.enteredBy : nil }
                             return Member(userId: m.userId, name: m.name, role: m.admin ? .admin : .member, me: m.userId == me, trip: t?.trip,
                                           tripId: m.tripId, enteredByAdmin: by.map { names[$0] ?? "" })
                         }, invite: inv, requests: reqs, meetups: meets)
        }
    }

    public func createGroup(name: String, myName: String, startsOn: Day?, endsOn: Day?, myTrip: Trip?) async throws -> String {
        let id = try await io {
            let tripId: String? = myTrip == nil ? nil : try await tripSync.sendNow(myTrip!)
            return try await groups.create(name: name, displayName: myName, startsOn: startsOn, endsOn: endsOn, tripId: tripId)
        }
        if profile["name"] == nil { remember(name: myName.trimmingCharacters(in: .whitespaces), provider: nil) }
        return id
    }

    public func updateGroup(_ id: String, name: String, startsOn: Day?, endsOn: Day?) async throws {
        try await io { try await groups.update(id, name: name, startsOn: startsOn, endsOn: endsOn) }
    }

    public func deleteGroup(_ id: String) async throws {
        try await io { try await groups.delete(id) }
        await sync.refreshMine()
    }

    public func newInvite(_ groupId: String, requiresApproval: Bool) async throws -> Invite {
        try await io {
            let i = try await groups.newInvite(groupId, requiresApproval: requiresApproval)
            return Invite(id: i.id, code: i.code, token: i.token, requiresApproval: i.requiresApproval, expiresAt: i.expiresAt.flatMap(ISOTime.parse))
        }
    }

    public func preview(_ code: String) async throws -> Preview {
        try await io {
            let p = try await groups.preview(code)
            guard let g = p.group else { return Preview(status: JoinStatus.of(p.status)) }
            return Preview(status: .ok, groupId: g.id, name: g.name, startsOn: g.startsOn, endsOn: g.endsOn, requiresApproval: p.requiresApproval,
                           alreadyMember: p.alreadyMember, members: p.members.map { ($0.userId, $0.name) })
        }
    }

    public func join(_ code: String, myName: String) async throws -> JoinResult {
        let r = try await io { let j = try await groups.join(code, displayName: myName); return JoinResult(JoinStatus.of(j.status), j.groupId) }
        if profile["name"] == nil { remember(name: myName.trimmingCharacters(in: .whitespaces), provider: nil) }
        if r.status == .pending { await sync.refreshMine() } // the request goes into my copy, to see its approval
        return r
    }

    public func reclaim(_ code: String, memberId: String) async throws -> JoinResult {
        let r = try await io { let j = try await groups.reclaim(code, memberId: memberId); return JoinResult(JoinStatus.of(j.status), j.groupId) }
        if r.status == .pending { await sync.refreshMine() }
        return r
    }

    public func pendingRequests() async throws -> [String] {
        guard server.stored() != nil else { return [] }
        return try await io {
            var seen: [String] = []
            for r in try await groups.myRequests() where !seen.contains(r.groupId) { seen.append(r.groupId) }
            return seen
        }
    }

    /// My groups in the phone's copy, live while watched. A group my request waited on that is now one of mine is an
    /// approval (also one decided while the app was closed: the copy remembers the request).
    public func watchMine(_ onChange: @escaping @MainActor ([String]) -> Void) -> () -> Void {
        let sync = self.sync
        let task = Task { @MainActor in
            var waiting: Set<String>? = nil
            let stream = await sync.watchMineStream()
            await sync.watchMine()
            for await m in stream {
                guard let w = waiting else { waiting = Set(m.requests.map(\.groupId)); continue }
                let approved = m.groups.map(\.id).filter { w.contains($0) }
                waiting = Set(m.requests.map(\.groupId))
                onChange(approved)
            }
        }
        return { task.cancel(); Task { await sync.unwatchMine() } }
    }

    public func decide(_ requestId: String, approve: Bool) async throws { try await io { try await groups.decide(requestId, approve: approve) } }
    public func leave(_ groupId: String) async throws {
        try await io { try await groups.leave(groupId) }
        await sync.refreshMine()
    }
    public func removeMember(_ groupId: String, userId: String) async throws { try await io { try await groups.remove(groupId, userId: userId) } }
    public func setRole(_ groupId: String, userId: String, role: Role) async throws { try await io { try await groups.setRole(groupId, userId: userId, role: role.rawValue) } }
    public func claimAdmin(_ groupId: String) async throws { try await io { try await groups.claimAdmin(groupId) } }
    public func revokeInvite(_ inviteId: String) async throws { try await io { try await groups.revokeInvite(inviteId) } }
    public func cancelRequest(_ groupId: String) async throws {
        try await io { for r in try await groups.myRequests() where r.groupId == groupId { try await groups.cancelRequest(r.id) } }
    }

    public func showMyTrip(_ groupId: String, myName: String, trip: Trip?) async throws {
        try await io {
            let tid: String? = trip == nil ? nil : try await tripSync.sendNow(trip!)
            try await groups.setMine(groupId, displayName: myName, tripId: tid)
        }
    }

    /// "I'm on the same flight": that flight becomes my trip (on the phone, by the caller, and as my one row on the
    /// server). The server overwrites the row the phone keeps, so the phone adopts the id it answers with.
    public func sameFlight(_ groupId: String, tripId: String) async throws -> Trip {
        try await io {
            guard let t = try await groups.flights(ids: [tripId])[tripId]?.trip else { throw ServerError("trip_not_in_group", 404) }
            let mine = try await groups.sameFlight(groupId, tripId: tripId, myTripId: tripSync.tripId)
            await tripSync.adopt(mine, trip: t)
            return t
        }
    }

    public func setMemberTrip(_ groupId: String, userId: String, trip: Trip) async throws {
        _ = try await io { try await groups.setMemberTrip(groupId, userId: userId, trip: trip) }
    }

    /// Mine, not one an admin filled in for me, the newest first; the phone adopts its row.
    public func myTripOnServer() async throws -> Trip? {
        guard let me = server.stored()?.userId else { return nil }
        return try await io {
            let e = ServerClient.enc(me)
            guard let o = try await server.select("trips", "select=*&owner_id=eq.\(e)&or=(entered_by.is.null,entered_by.eq.\(e))&order=updated_at.desc&limit=1").first,
                  let t = TripRow.trip(o) else { return nil }
            await tripSync.adopt(o.string("id"), trip: t)
            return t
        }
    }

    public func leaderboard(_ groupId: String, game: String) async throws -> [Score] {
        let me = server.stored()?.userId
        let rows: [Groups.Score]
        do {
            rows = try await groups.leaderboard(groupId, game: game)
        } catch let e as ServerError {
            throw ApiError(e.code == "no_session" ? "not_signed_in" : e.code)
        } catch {
            // no signal: the table the phone keeps for this game, if any
            guard let kept = await sync.snapshot(groupId).scores[game] else { throw ApiError("offline") }
            rows = kept
        }
        return rows.map { Score(userId: $0.userId, name: $0.name, best: $0.best, me: $0.userId == me) }
    }

    public func submitBest(game: String, score: Int) async -> Bool {
        guard server.stored() != nil else { return false }
        var first = (try? await groups.mine())?.first?.id
        if first == nil { first = await sync.myGroups.groups.first?.id }
        guard let g = first else { return false }
        await sync.submitScore(g, game: game, score: score)
        return true
    }

    /// Through the queue on the phone: shown in the group at once, sent when there is signal.
    public func addMeetup(_ groupId: String, station: String, at: Date) async throws { await sync.addMeetup(groupId, station: station, at: at) }

    public func removeMeetup(_ groupId: String, meetupId: String) async throws { await sync.removeMeetup(groupId, id: meetupId) }

    public func tripSaved(_ trip: Trip?) async { await tripSync.pushed(trip) }

    /// On launch: what waits goes out (the queue and my trip).
    public func flush() async {
        await tripSync.flush()
        await sync.flush()
    }
}

/// One group from the phone's copy ([Sync]), as the page reads it.
@MainActor
final class LiveWatch: GroupWatch {
    private let id: String
    private let sync: Sync
    private let me: @MainActor () -> String?
    private(set) var now: Shown

    init(_ id: String, sync: Sync, me: @escaping @MainActor () -> String?) {
        self.id = id; self.sync = sync; self.me = me
        now = Shown(group: nil)
    }

    func states() -> AsyncStream<Shown> {
        let (s, c) = AsyncStream<Shown>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let sync = self.sync, id = self.id
        let task = Task { @MainActor [weak self] in
            for await snap in await sync.states(id) {
                let shown = LiveGroupAPI.shown(snap, me: self?.me())
                self?.now = shown
                c.yield(shown)
            }
            c.finish()
        }
        c.onTermination = { _ in task.cancel() }
        return s
    }

    func open() async { await sync.open(id) }
    func close() async { await sync.close(id) }
    func refresh() async { await sync.refresh(id) }
    func clearRefused() async { await sync.clearRefused(id) }
}
