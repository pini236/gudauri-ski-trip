import Foundation

/// The group's data on the phone (decision 28, server/CONTRACT.md "עותק מקומי"), as Android's server/Sync.kt: what the
/// screens show comes from here, never straight from the network, so they open at once and keep working with no signal.
///
/// - Every group the person opened is kept as a file in the app's private storage, with the time it was read.
/// - [open] (a screen is showing) reads it again and listens to Realtime; a change reads it again.
/// - Writes made here show at once and wait in a queue (a file) until the server takes them: scores (only a higher one
///   counts), meetups (the last one saved wins), my name and trip in the group. A write the server refuses is dropped
///   and the group read again, and the refusal's code stays on the group ([Snapshot.refused]) until it is dismissed.
/// - Everything else (joining, admin actions) needs signal: call [Groups] directly, then [refresh].
/// A queue that cannot get through is tried again by itself after 5 seconds, then 10, 20... up to 5 minutes.
public actor Sync {
    public nonisolated let server: ServerClient
    public nonisolated let groups: Groups
    private let dir: URL
    /// The games whose group tables are kept ("descent"...), as sent to submit_score.
    private let games: [String]
    private let realtime: Realtime?
    /// Waits the given seconds (the tests pass one that records and returns at once).
    private let sleep: @Sendable (Double) async -> Void

    public static let firstRetry: Double = 5
    public static let maxRetry: Double = 300

    /// The groups I am in, kept like a group is.
    public struct Mine: Sendable, Equatable {
        public var groups: [Groups.Group] = []
        public var requests: [Groups.JoinRequest] = []
        public var readAt: Int64? = nil
        public var offline = false
        public init(groups: [Groups.Group] = [], requests: [Groups.JoinRequest] = [], readAt: Int64? = nil, offline: Bool = false) {
            self.groups = groups; self.requests = requests; self.readAt = readAt; self.offline = offline
        }
    }

    /// One group as the screens show it. [readAt] (ms) is nil until it was read once; [gone]: I am no longer in it.
    public struct Snapshot: Sendable, Equatable {
        public var groupId: String
        public var group: Groups.Group? = nil
        public var members: [Groups.Member] = []
        public var flights: [String: Trip] = [:]
        /// Trips an admin entered for a member (Q7): trip id to the admin's user id.
        public var enteredBy: [String: String] = [:]
        public var meetups: [Groups.Meetup] = []
        public var scores: [String: [Groups.Score]] = [:]
        /// The working invite: every member sees it and may share it.
        public var invite: Groups.Invite? = nil
        /// Requests waiting for an admin; empty for a member who is not one.
        public var requests: [Groups.JoinRequest] = []
        public var readAt: Int64? = nil
        public var offline = false
        public var gone = false
        /// Writes made here that the server has not taken yet.
        public var waiting = 0
        /// The code of the last write here that the server refused for good, until the page dismisses it (ד5).
        public var refused: String? = nil
        public init(groupId: String) { self.groupId = groupId }
        public func me(_ userId: String?) -> Groups.Member? { members.first { $0.userId == userId } }
    }

    struct Op: Sendable, Equatable {
        var id: String, kind: String, groupId: String, body: JSON
    }

    private var snaps: [String: Snapshot] = [:]
    private var watchers: [String: [UUID: AsyncStream<Snapshot>.Continuation]] = [:]
    private var channels: [String: Realtime.Channel] = [:]
    private var refreshing: Set<String> = []
    private var mineNow: Mine
    private var mineWatchers: [UUID: AsyncStream<Mine>.Continuation] = [:]
    private var mineChannel: Realtime.Channel?
    private var outbox: [Op]
    private var flushing = false
    private var flushAgain = false
    private var retryWait: Double = 0
    private var retryPlanned = false

    public init(server: ServerClient, dir: URL, games: [String] = [], realtime: Realtime? = nil,
                sleep: @escaping @Sendable (Double) async -> Void = { s in try? await Task.sleep(nanoseconds: UInt64(s * 1e9)) }) {
        self.server = server
        groups = Groups(server)
        self.dir = dir
        self.games = games
        self.realtime = realtime
        self.sleep = sleep
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        mineNow = Codec.mine(JSON.parse((try? Data(contentsOf: dir.appendingPathComponent("mine.json"))) ?? Data()))
        outbox = Codec.ops(JSON.parse((try? Data(contentsOf: dir.appendingPathComponent("outbox.json"))) ?? Data()))
    }

    // MARK: - My groups

    public var myGroups: Mine { mineNow }

    public func watchMineStream() -> AsyncStream<Mine> {
        let id = UUID()
        let (s, c) = AsyncStream<Mine>.makeStream(bufferingPolicy: .bufferingNewest(1))
        c.yield(mineNow)
        mineWatchers[id] = c
        c.onTermination = { [weak self] _ in Task { await self?.dropMineWatcher(id) } }
        return s
    }

    private func dropMineWatcher(_ id: UUID) { mineWatchers[id] = nil }

    private func setMine(_ m: Mine) {
        mineNow = m
        for c in mineWatchers.values { c.yield(m) }
    }

    public func refreshMine() async {
        do {
            let list = try await groups.mine()
            let req = try await groups.myRequests()
            let m = Mine(groups: list, requests: req, readAt: server.nowSeconds() * 1000)
            setMine(m)
            FileStore.write(dir.appendingPathComponent("mine.json"), Codec.mine(m).data)
        } catch is ServerError {
            var m = mineNow; m.offline = false; setMine(m)
        } catch {
            var m = mineNow; m.offline = true; setMine(m)
        }
    }

    /// Watch my groups list (approvals and removals show without a reload).
    public func watchMine() async {
        guard let me = server.userId() else { return }
        if mineChannel == nil, let rt = realtime {
            mineChannel = await rt.join("me:\(me)", [
                .init("group_members", filter: "user_id=eq.\(me)"), .init("group_members", event: "DELETE"),
                .init("join_requests", filter: "user_id=eq.\(me)"), .init("groups"),
            ], onChange: { [weak self] _ in Task { await self?.refreshMine() } },
               onReconnect: { [weak self] in Task { await self?.flush(); await self?.refreshMine() } })
        }
        await refreshMine()
    }

    public func unwatchMine() async {
        if let c = mineChannel { await realtime?.leave(c) }
        mineChannel = nil
    }

    // MARK: - One group

    public func snapshot(_ groupId: String) -> Snapshot { load(groupId) }

    /// The group as it changes, starting with what the phone has now.
    public func states(_ groupId: String) -> AsyncStream<Snapshot> {
        let id = UUID()
        let (s, c) = AsyncStream<Snapshot>.makeStream(bufferingPolicy: .bufferingNewest(1))
        c.yield(load(groupId))
        watchers[groupId, default: [:]][id] = c
        c.onTermination = { [weak self] _ in Task { await self?.dropWatcher(groupId, id) } }
        return s
    }

    private func dropWatcher(_ g: String, _ id: UUID) { watchers[g]?[id] = nil }

    private func load(_ g: String) -> Snapshot {
        if let s = snaps[g] { return s }
        let s = Codec.snapshot(JSON.parse((try? Data(contentsOf: file(g))) ?? Data()), now: Date()) ?? Snapshot(groupId: g)
        snaps[g] = s
        return s
    }

    private func set(_ s: Snapshot) {
        snaps[s.groupId] = s
        for c in (watchers[s.groupId] ?? [:]).values { c.yield(s) }
    }

    private func file(_ g: String) -> URL { dir.appendingPathComponent("groups").appendingPathComponent("\(g).json") }

    /// A screen shows this group: read it and listen until [close].
    public func open(_ groupId: String) async {
        if channels[groupId] == nil, let rt = realtime {
            let g = groupId
            channels[groupId] = await rt.join("group:\(g)", [
                .init("groups", filter: "id=eq.\(g)"), .init("group_members", filter: "group_id=eq.\(g)"),
                .init("meetups", filter: "group_id=eq.\(g)"), .init("invites", filter: "group_id=eq.\(g)"),
                .init("join_requests", filter: "group_id=eq.\(g)"),
                // deletions cannot be filtered (Supabase); trips and scores have no group: the row rules decide
                .init("group_members", event: "DELETE"), .init("meetups", event: "DELETE"), .init("trips"), .init("scores"),
            ], onChange: { [weak self] _ in Task { await self?.refresh(g) } },
               onReconnect: { [weak self] in Task { await self?.flush(); await self?.refresh(g) } })
        }
        await refresh(groupId)
    }

    public func close(_ groupId: String) async {
        if let c = channels.removeValue(forKey: groupId) { await realtime?.leave(c) }
    }

    /// Read again. Several calls close together read once.
    public func refresh(_ groupId: String) async {
        guard !refreshing.contains(groupId) else { return }
        refreshing.insert(groupId)
        defer { refreshing.remove(groupId) }
        await readFromServer(groupId)
    }

    func readFromServer(_ groupId: String) async {
        do {
            // inside the try: renewing the session may need the network and may fail like any call
            let me = try await server.current()?.userId
            let members = try await groups.members(groupId)
            guard let me, let mine = members.first(where: { $0.userId == me }) else {
                var s = Snapshot(groupId: groupId); s.gone = true; s.readAt = server.nowSeconds() * 1000
                set(s)
                try? FileManager.default.removeItem(at: file(groupId))
                return
            }
            var s = Snapshot(groupId: groupId)
            s.group = try await groups.head(groupId)
            s.members = members
            let flights = try await groups.flights(ids: members.compactMap(\.tripId))
            s.flights = flights.mapValues(\.trip)
            s.enteredBy = flights.values.filter(\.byAdmin).reduce(into: [:]) { $0[$1.id] = $1.enteredBy! }
            s.meetups = try await groups.meetups(groupId)
            for g in games { s.scores[g] = try await groups.leaderboard(groupId, game: g) }
            s.invite = try await groups.invite(groupId) // every member may share it (docs/USERS.md)
            s.requests = mine.admin ? try await groups.requests(groupId) : []
            s.readAt = server.nowSeconds() * 1000
            // what waits in the queue still shows, on top of what the server sent
            for op in outbox where op.groupId == groupId { s = applyLocal(s, op, me) }
            s.waiting = outbox.filter { $0.groupId == groupId }.count
            s.refused = load(groupId).refused
            set(s)
            FileStore.write(file(groupId), Codec.snapshot(s).data)
        } catch let e as ServerError {
            // not mine any more, or the session is gone: keep what is shown, it is marked as not fresh
            var s = load(groupId); s.offline = e.code == "no_session" || e.code == ServerClient.badResponse; set(s)
        } catch {
            var s = load(groupId); s.offline = true; set(s)
        }
    }

    /// The page showed the refusal, and the person closed it.
    public func clearRefused(_ groupId: String) { var s = load(groupId); s.refused = nil; set(s) }

    // MARK: - Writes that wait for signal

    public func submitScore(_ groupId: String, game: String, score: Int) async {
        await queue("score", groupId, .obj(["game": .string(game), "score": .int(score)]))
    }

    public func setMine(_ groupId: String, displayName: String, tripId: String?) async {
        await queue("my_membership", groupId, .obj(["name": .string(displayName.trimmingCharacters(in: .whitespaces)), "trip": .string(tripId ?? "")]))
    }

    /// Shows at once with a temporary id ("local:..."), which becomes the server's when it is sent.
    @discardableResult
    public func addMeetup(_ groupId: String, station: String, at: Date, note: String? = nil) async -> String {
        let local = "local:" + UUID().uuidString.lowercased()
        await queue("meetup_add", groupId, .obj(["local": .string(local), "station": .string(station), "at": .string(ISOTime.text(at)), "note": .string(note ?? "")]))
        return local
    }

    public func changeMeetup(_ groupId: String, id: String, station: String, at: Date, note: String? = nil) async {
        let body = JSON.obj(["station": .string(station), "at": .string(ISOTime.text(at)), "note": .string(note ?? "")])
        // a meetup not sent yet: change what will be sent
        if id.hasPrefix("local:"), let i = outbox.firstIndex(where: { $0.kind == "meetup_add" && $0.body.string("local") == id }) {
            outbox[i].body = body.with("local", .string(id))
            saveOutbox()
            set(applyLocal(load(groupId), outbox[i], server.userId()))
            return
        }
        await queue("meetup_change", groupId, body.with("id", .string(id)))
    }

    public func removeMeetup(_ groupId: String, id: String) async {
        if id.hasPrefix("local:") {
            outbox.removeAll { ($0.kind == "meetup_add" && $0.body.string("local") == id) || $0.body.string("id") == id }
            saveOutbox()
        } else {
            await queue("meetup_remove", groupId, .obj(["id": .string(id)]), flushNow: false)
        }
        var s = load(groupId)
        s.meetups.removeAll { $0.id == id }
        s.waiting = outbox.filter { $0.groupId == groupId }.count
        set(s)
        if !id.hasPrefix("local:") { await flush() }
    }

    private func queue(_ kind: String, _ groupId: String, _ body: JSON, flushNow: Bool = true) async {
        // a newer score for the same game, or my name again, replaces the older one still waiting
        if kind == "my_membership" { outbox.removeAll { $0.kind == kind && $0.groupId == groupId } }
        if kind == "score" {
            outbox.removeAll { $0.kind == kind && $0.body.string("game") == body.string("game") && ($0.body.int("score") ?? 0) <= (body.int("score") ?? 0) }
        }
        let op = Op(id: UUID().uuidString, kind: kind, groupId: groupId, body: body)
        outbox.append(op)
        saveOutbox()
        var s = applyLocal(load(groupId), op, server.userId())
        s.waiting = outbox.filter { $0.groupId == groupId }.count
        set(s)
        if flushNow { await flush() }
    }

    var queued: [Op] { outbox }
    public var nextRetry: Double { retryWait }

    /// Send what waits (call on launch and when signal comes back).
    public func flush() async {
        if flushing { flushAgain = true; return }
        flushing = true
        defer { flushing = false }
        repeat {
            flushAgain = false
            await sendOutbox()
        } while flushAgain
    }

    private func sendOutbox() async {
        var touched: [String] = []
        var stuck = false
        while let op = outbox.first {
            do {
                try await send(op)
                outbox.removeAll { $0.id == op.id }
                saveOutbox()
            } catch let e as ServerError {
                // no session, a server hiccup (5xx), a busy server (429), a timeout (408) or an answer that is not
                // JSON: it stays in the queue for next time
                if e.code == "no_session" { break }
                if e.code == ServerClient.badResponse || e.status >= 500 || e.status == 429 || e.status == 408 { stuck = true; break }
                // refused for good: drop it, and read the group again so the screen shows what the server has, and why
                outbox.removeAll { $0.id == op.id }
                saveOutbox()
                var s = load(op.groupId); s.refused = e.code; set(s)
            } catch {
                stuck = true
                break
            }
            if !touched.contains(op.groupId) { touched.append(op.groupId) }
        }
        planRetry(stuck)
        for g in touched where watchers[g]?.isEmpty == false || channels[g] != nil { await readFromServer(g) }
        for g in snaps.keys {
            var s = load(g); let n = outbox.filter { $0.groupId == g }.count
            if s.waiting != n { s.waiting = n; set(s) }
        }
    }

    // A queue that could not get through is tried again after 5 s, then 10, 20... up to 5 minutes; one that went
    // through starts the count again. (No session: only signing in helps, so no retry is planned.)
    private func planRetry(_ stuck: Bool) {
        guard stuck else { retryWait = 0; return }
        retryWait = retryWait == 0 ? Self.firstRetry : min(retryWait * 2, Self.maxRetry)
        guard !retryPlanned else { return }
        retryPlanned = true
        let wait = retryWait
        Task { [weak self, sleep] in
            await sleep(wait)
            await self?.retryNow()
        }
    }

    private func retryNow() async {
        retryPlanned = false
        await flush()
    }

    private func send(_ op: Op) async throws {
        let b = op.body
        switch op.kind {
        case "score": _ = try await groups.submitScore(game: b.string("game"), score: b.int("score") ?? 0)
        case "my_membership":
            let t = b.string("trip")
            try await groups.setMine(op.groupId, displayName: b.string("name"), tripId: t.isEmpty ? nil : t)
        case "meetup_add":
            let local = b.string("local")
            let note = b.string("note")
            guard let at = ISOTime.parse(b.string("at")) else { return }
            // the temporary id's own part is the id the server gets, so a repeat of this write finds the same meetup
            let m = try await groups.addMeetup(op.groupId, station: b.string("station"), at: at, note: note.isEmpty ? nil : note,
                                               id: String(local.dropFirst("local:".count)))
            for i in outbox.indices where outbox[i].body.string("id") == local { outbox[i].body = outbox[i].body.with("id", .string(m.id)) }
            saveOutbox()
            var s = load(op.groupId)
            s.meetups = s.meetups.map { $0.id == local ? m : $0 }
            set(s)
        case "meetup_change":
            let note = b.string("note")
            guard let at = ISOTime.parse(b.string("at")) else { return }
            _ = try await groups.changeMeetup(b.string("id"), station: b.string("station"), at: at, note: note.isEmpty ? nil : note)
        case "meetup_remove": try await groups.removeMeetup(b.string("id"))
        default: break
        }
    }

    /// A write that waits, shown on top of the last read.
    private func applyLocal(_ s0: Snapshot, _ op: Op, _ me: String?) -> Snapshot {
        var s = s0
        let b = op.body
        switch op.kind {
        case "score":
            let game = b.string("game"), score = b.int("score") ?? 0
            guard let me, let name = s.me(me)?.name else { return s }
            let table = s.scores[game] ?? []
            if let now = table.first(where: { $0.userId == me }), now.best >= score { return s }
            s.scores[game] = (table.filter { $0.userId != me } + [Groups.Score(me, name, score)]).sorted { $0.best > $1.best }
        case "my_membership":
            let t = b.string("trip")
            s.members = s.members.map { m in
                guard m.userId == me else { return m }
                var m = m; m.name = b.string("name"); m.tripId = t.isEmpty ? nil : t; return m
            }
        case "meetup_add":
            guard let at = ISOTime.parse(b.string("at")) else { return s }
            let note = b.string("note")
            let m = Groups.Meetup(id: b.string("local"), groupId: s.groupId, station: b.string("station"), at: at, note: note.isEmpty ? nil : note, createdBy: me)
            s.meetups = (s.meetups.filter { $0.id != m.id } + [m]).sorted { $0.at < $1.at }
        case "meetup_change":
            guard let at = ISOTime.parse(b.string("at")) else { return s }
            let note = b.string("note")
            s.meetups = s.meetups.map { m in
                guard m.id == b.string("id") else { return m }
                var m = m; m.station = b.string("station"); m.at = at; m.note = note.isEmpty ? nil : note; return m
            }.sorted { $0.at < $1.at }
        case "meetup_remove": s.meetups.removeAll { $0.id == b.string("id") }
        default: break
        }
        return s
    }

    private func saveOutbox() { FileStore.write(dir.appendingPathComponent("outbox.json"), Codec.ops(outbox).data) }

    /// After deleting the account or signing out: nothing of the groups stays on the phone.
    public func forget() async {
        await unwatchMine()
        for (_, c) in channels { await realtime?.leave(c) }
        channels = [:]
        outbox = []
        snaps = [:]
        if let items = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            for u in items { try? FileManager.default.removeItem(at: u) }
        }
        setMine(Mine())
    }

    // MARK: - Files

    /// The snapshot as JSON on the phone (its own small format; v1, the same fields as Android's).
    enum Codec {
        static func group(_ g: Groups.Group) -> JSON {
            .obj(["id": .string(g.id), "name": .string(g.name), "starts_on": .str(g.startsOn?.iso), "ends_on": .str(g.endsOn?.iso)])
        }
        static func request(_ r: Groups.JoinRequest) -> JSON {
            .obj(["id": .string(r.id), "group_id": .string(r.groupId), "user_id": .string(r.userId), "display_name": .string(r.name),
                  "kind": .string(r.kind), "reclaim_user_id": .str(r.reclaimUserId), "status": .string(r.status)])
        }

        static func mine(_ m: Mine) -> JSON {
            .obj(["groups": .array(m.groups.map(group)), "requests": .array(m.requests.map(request)), "readAt": .number(Double(m.readAt ?? 0))])
        }

        static func mine(_ o: JSON?) -> Mine {
            guard let o, o.object != nil else { return Mine() }
            let at = Int64(o.int("readAt") ?? 0)
            return Mine(groups: o.objects("groups").map { Groups.group($0, idKey: "id") }, requests: o.objects("requests").map(Groups.request),
                        readAt: at > 0 ? at : nil)
        }

        static func ops(_ ops: [Op]) -> JSON {
            .array(ops.map { .obj(["id": .string($0.id), "kind": .string($0.kind), "group": .string($0.groupId), "body": $0.body]) })
        }

        static func ops(_ o: JSON?) -> [Op] {
            (o?.objects ?? []).map { Op(id: $0.string("id"), kind: $0.string("kind"), groupId: $0.string("group"), body: $0["body"] ?? .object([:])) }
        }

        static func snapshot(_ s: Snapshot) -> JSON {
            .obj([
                "v": 1, "group_id": .string(s.groupId), "group": s.group.map(group) ?? .null,
                "members": .array(s.members.map { .obj(["user_id": .string($0.userId), "name": .string($0.name), "role": .string($0.role), "trip_id": .str($0.tripId)]) }),
                "flights": .object(s.flights.mapValues(TripRow.of)),
                "entered_by": .object(s.enteredBy.mapValues { .string($0) }),
                "meetups": .array(s.meetups.map { .obj(["id": .string($0.id), "station": .string($0.station), "at": .string(ISOTime.text($0.at)),
                                                        "note": .str($0.note), "created_by": .str($0.createdBy)]) }),
                "scores": .object(s.scores.mapValues { .array($0.map { .obj(["user_id": .string($0.userId), "name": .string($0.name), "best": .int($0.best)]) }) }),
                "invite": s.invite.map { i in .obj(["id": .string(i.id), "code": .string(i.code), "token": .string(i.token), "requires_approval": .bool(i.requiresApproval),
                                                    "max_uses": .int(i.maxUses), "expires_at": .str(i.expiresAt), "uses": .int(i.uses),
                                                    "valid_until": .str(i.validUntil.map(ISOTime.text))]) } ?? .null,
                "requests": .array(s.requests.map(request)),
                "read_at": .number(Double(s.readAt ?? 0)),
            ])
        }

        static func snapshot(_ o: JSON?, now: Date) -> Snapshot? {
            guard let o, o.int("v") == 1 else { return nil }
            var s = Snapshot(groupId: o.string("group_id"))
            if let g = o["group"], g.object != nil { s.group = Groups.group(g, idKey: "id") }
            s.members = o.objects("members").map { Groups.Member($0.string("user_id"), $0.string("name"), role: $0.string("role"), tripId: $0.str("trip_id")) }
            for (k, v) in o["flights"]?.object ?? [:] { if let t = TripRow.trip(v) { s.flights[k] = t } }
            for (k, v) in o["entered_by"]?.object ?? [:] { if case .string(let by) = v { s.enteredBy[k] = by } }
            s.meetups = o.objects("meetups").compactMap { m in
                ISOTime.parse(m.string("at")).map { Groups.Meetup(id: m.string("id"), groupId: s.groupId, station: m.string("station"), at: $0, note: m.str("note"), createdBy: m.str("created_by")) }
            }
            for (g, v) in o["scores"]?.object ?? [:] { s.scores[g] = v.objects.map { Groups.Score($0.string("user_id"), $0.string("name"), $0.int("best") ?? 0) } }
            if let i = o["invite"], i.object != nil {
                let inv = Groups.Invite(id: i.string("id"), code: i.string("code"), token: i.string("token"), requiresApproval: i.bool("requires_approval"),
                                        maxUses: i.int("max_uses"), expiresAt: i.str("expires_at"), uses: i.int("uses") ?? 0,
                                        validUntil: i.str("valid_until").flatMap(ISOTime.parse))
                // one that expired while the phone was offline is not offered for sharing
                if inv.working(now) { s.invite = inv }
            }
            s.requests = o.objects("requests").map(Groups.request)
            let at = Int64(o.int("read_at") ?? 0)
            s.readAt = at > 0 ? at : nil
            return s
        }
    }
}
