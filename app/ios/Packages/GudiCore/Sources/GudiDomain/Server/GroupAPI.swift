import Foundation

// Accounts and groups as the screens see them (docs/USERS.md, round 10: A1 to A5, Q1 to Q10), as Android's
// group/GroupApi.kt. The screens talk to the server only through [GroupAPI]: [LiveGroupAPI] over the client here, or
// [FakeGroupAPI], the pretend server of the screenshots (made-up people only).

public enum Role: String, Sendable { case admin, member }

/// Who this phone is on the server: nobody yet (nil), a guest (an anonymous identity), or a registered account.
public struct Me: Sendable, Equatable {
    public var userId: String
    public var registered: Bool
    public var name: String?
    public var google = false
    public var apple = false
    public init(userId: String, registered: Bool, name: String?, google: Bool = false, apple: Bool = false) {
        self.userId = userId; self.registered = registered; self.name = name; self.google = google; self.apple = apple
    }
}

public struct GroupSummary: Sendable, Equatable, Identifiable {
    public var id: String, name: String, startsOn: Day?, endsOn: Day?
    public init(id: String, name: String, startsOn: Day?, endsOn: Day?) { self.id = id; self.name = name; self.startsOn = startsOn; self.endsOn = endsOn }
}

extension Array where Element == GroupSummary {
    /// My groups in the site's order (its account list, K-4): by start date, the undated last, ties as they came.
    public func byStart() -> [GroupSummary] {
        enumerated().sorted { a, b in
            switch (a.element.startsOn, b.element.startsOn) {
            case let (x?, y?): return x != y ? x < y : a.offset < b.offset
            case (_?, nil): return true
            case (nil, _?): return false
            default: return a.offset < b.offset
            }
        }.map(\.element)
    }
}

/// A row of "my groups" in the account (K-4): how many are in it (nil: not known here) and whether I run it.
public struct MyGroup: Sendable, Equatable, Identifiable {
    public var id: String, name: String, members: Int?, admin: Bool
}

public struct Member: Sendable, Equatable, Identifiable {
    public var userId: String
    public var name: String
    public var role: Role
    public var me: Bool
    /// The trip this member shows in the group, and its id there (for "I'm on the same flight").
    public var trip: Trip?
    public var tripId: String?
    /// An admin filled it in for them: the admin's name.
    public var enteredByAdmin: String?
    public var registered: Bool
    public var id: String { userId }
    public init(userId: String, name: String, role: Role, me: Bool, trip: Trip? = nil, tripId: String? = nil, enteredByAdmin: String? = nil, registered: Bool = false) {
        self.userId = userId; self.name = name; self.role = role; self.me = me; self.trip = trip; self.tripId = tripId
        self.enteredByAdmin = enteredByAdmin; self.registered = registered
    }
}

public struct Invite: Sendable, Equatable {
    public var id, code, token: String
    public var requiresApproval: Bool
    public var expiresAt: Date?
    public init(id: String, code: String, token: String, requiresApproval: Bool, expiresAt: Date? = nil) {
        self.id = id; self.code = code; self.token = token; self.requiresApproval = requiresApproval; self.expiresAt = expiresAt
    }
}

public struct JoinRequest: Sendable, Equatable, Identifiable {
    public var id, userId, name: String
    public var reclaim: Bool
    public init(id: String, userId: String, name: String, reclaim: Bool) { self.id = id; self.userId = userId; self.name = name; self.reclaim = reclaim }
}

public struct Meetup: Sendable, Equatable, Identifiable {
    public var id, station: String
    public var at: Date
    public var note: String?
    public var byName: String?
    public init(id: String, station: String, at: Date, note: String? = nil, byName: String? = nil) {
        self.id = id; self.station = station; self.at = at; self.note = note; self.byName = byName
    }
}

public struct Group: Sendable, Equatable, Identifiable {
    public var id, name: String
    public var startsOn, endsOn: Day?
    public var members: [Member]
    /// The working invite (every member shares it).
    public var invite: Invite?
    /// Pending requests: only admins see them.
    public var requests: [JoinRequest]
    public var meetups: [Meetup]
    public init(id: String, name: String, startsOn: Day?, endsOn: Day?, members: [Member], invite: Invite?, requests: [JoinRequest], meetups: [Meetup]) {
        self.id = id; self.name = name; self.startsOn = startsOn; self.endsOn = endsOn; self.members = members
        self.invite = invite; self.requests = requests; self.meetups = meetups
    }
    public var me: Member? { members.first { $0.me } }
    public var admin: Bool { me?.role == .admin }
    public var admins: Int { members.filter { $0.role == .admin }.count }
}

/// A group as its page shows it, from the phone first: [group] is nil until it was read once, [readAt] (ms) is when.
public struct Shown: Sendable, Equatable {
    public var group: Group?
    public var readAt: Int64? = nil
    public var offline = false
    public var gone = false
    public var waiting = false
    /// A change made here that waited for signal, and the server refused: its code, until cleared.
    public var refused: String? = nil
    public init(group: Group?, readAt: Int64? = nil, offline: Bool = false, gone: Bool = false, waiting: Bool = false, refused: String? = nil) {
        self.group = group; self.readAt = readAt; self.offline = offline; self.gone = gone; self.waiting = waiting; self.refused = refused
    }
}

/// One group kept on the phone: shows at once, also with no signal; reads again on [open] and on each change until
/// [close]. After a change made on the page (approving, roles, the same flight), [refresh].
@MainActor
public protocol GroupWatch: AnyObject {
    var now: Shown { get }
    func states() -> AsyncStream<Shown>
    func open() async
    func close() async
    func refresh() async
    func clearRefused() async
}

/// What an invite opens (invite_preview): the group, and its members' names for "I'm already in the group".
public struct Preview: Sendable, Equatable {
    public var status: JoinStatus
    public var groupId: String? = nil
    public var name = ""
    public var startsOn: Day? = nil
    public var endsOn: Day? = nil
    public var requiresApproval = false
    public var alreadyMember = false
    /// (user id, name)
    public var members: [(String, String)] = []
    public init(status: JoinStatus, groupId: String? = nil, name: String = "", startsOn: Day? = nil, endsOn: Day? = nil,
                requiresApproval: Bool = false, alreadyMember: Bool = false, members: [(String, String)] = []) {
        self.status = status; self.groupId = groupId; self.name = name; self.startsOn = startsOn; self.endsOn = endsOn
        self.requiresApproval = requiresApproval; self.alreadyMember = alreadyMember; self.members = members
    }
    public static func == (a: Preview, b: Preview) -> Bool {
        a.status == b.status && a.groupId == b.groupId && a.name == b.name && a.members.map(\.0) == b.members.map(\.0)
    }
}

/// The join actions answer with a status, not an error (server/CONTRACT.md).
public enum JoinStatus: String, Sendable {
    case ok, joined, pending, already_member, invalid_code, rate_limited, group_full, no_such_member, sign_in_instead
    public static func of(_ s: String?) -> JoinStatus { JoinStatus(rawValue: (s ?? "").lowercased()) ?? .invalid_code }
}

public struct JoinResult: Sendable, Equatable {
    public var status: JoinStatus
    public var groupId: String?
    public init(_ status: JoinStatus, _ groupId: String? = nil) { self.status = status; self.groupId = groupId }
}

public struct Score: Sendable, Equatable, Identifiable {
    public var userId, name: String
    public var best: Int
    public var me: Bool
    public var id: String { userId }
    public init(userId: String, name: String, best: Int, me: Bool) { self.userId = userId; self.name = name; self.best = best; self.me = me }
}

/// A refusal from the server, with its short code (not_admin, last_admin...), or "offline", "cancelled".
public struct ApiError: Error, Sendable, Equatable {
    public let code: String
    public init(_ code: String) { self.code = code }
}

/// The screens' door to accounts and groups. Every call may throw [ApiError].
@MainActor
public protocol GroupAPI: AnyObject {
    /// The identity this phone has now, from the saved session; no network.
    func me() -> Me?
    func signInGuest() async throws -> Me
    /// An ID token from Apple's or Google's sheet ("apple" or "google"), with the raw nonce it was asked with.
    func signIn(provider: String, idToken: String, nonce: String?) async throws -> Me
    func signOut() async throws
    func deleteAccount() async throws
    /// The name on my profile, which reaches every group (decision 50).
    func rename(_ name: String) async throws

    func myGroups() async throws -> [GroupSummary]
    func group(_ id: String) async throws -> Group
    func watch(_ id: String) -> GroupWatch
    func createGroup(name: String, myName: String, startsOn: Day?, endsOn: Day?, myTrip: Trip?) async throws -> String
    func updateGroup(_ id: String, name: String, startsOn: Day?, endsOn: Day?) async throws
    func deleteGroup(_ id: String) async throws
    /// A new invite, replacing the working one (revoke and replace).
    func newInvite(_ groupId: String, requiresApproval: Bool) async throws -> Invite

    func preview(_ code: String) async throws -> Preview
    func join(_ code: String, myName: String) async throws -> JoinResult
    func reclaim(_ code: String, memberId: String) async throws -> JoinResult
    func decide(_ requestId: String, approve: Bool) async throws
    func leave(_ groupId: String) async throws
    func removeMember(_ groupId: String, userId: String) async throws
    func setRole(_ groupId: String, userId: String, role: Role) async throws
    /// "Be the admin" (claim_admin): only while the group has no admin, and only for a registered member.
    func claimAdmin(_ groupId: String) async throws
    func revokeInvite(_ inviteId: String) async throws
    /// My request to join this group, still waiting for an admin, is withdrawn.
    func cancelRequest(_ groupId: String) async throws
    /// The groups my requests still wait on.
    func pendingRequests() async throws -> [String]
    /// Watch my groups while a screen waits on them: [onChange] after each change, with the groups an admin has just
    /// let me into. Returns how to stop.
    func watchMine(_ onChange: @escaping @MainActor ([String]) -> Void) -> () -> Void

    /// My trip in this group (nil: none shown).
    func showMyTrip(_ groupId: String, myName: String, trip: Trip?) async throws
    /// "I'm on the same flight": that trip becomes mine, in this group; returned to keep on the phone too.
    func sameFlight(_ groupId: String, tripId: String) async throws -> Trip
    /// A phone with no trip of its own, just signed in: my newest trip on the server (the site's pullTrip).
    func myTripOnServer() async throws -> Trip?
    func setMemberTrip(_ groupId: String, userId: String, trip: Trip) async throws

    func leaderboard(_ groupId: String, game: String) async throws -> [Score]
    /// A game's high score goes to the groups' tables (through the queue). False when I am in no group.
    func submitBest(game: String, score: Int) async -> Bool
    func addMeetup(_ groupId: String, station: String, at: Date) async throws
    func removeMeetup(_ groupId: String, meetupId: String) async throws
    /// "Your trip" was saved on the phone (nil: deleted): to the server too, when this phone has a session.
    func tripSaved(_ trip: Trip?) async
}

extension GroupAPI {
    /// "My groups" (K-4), each one's size and my role from the phone's copy when there is one.
    public func myGroupRows() async throws -> [MyGroup] {
        guard me() != nil else { return [] }
        var out: [MyGroup] = []
        for g in try await myGroups() {
            var full = watch(g.id).now.group
            if full == nil { full = try? await group(g.id) }
            out.append(MyGroup(id: g.id, name: g.name, members: full?.members.count, admin: full?.admin == true))
        }
        return out
    }

    /// The group sign on home: my first group's name and how many are in it; nil when I am in none.
    public func firstGroup() async -> (String, Int)? {
        guard me() != nil, let g = (try? await myGroups())?.first else { return nil }
        var n = watch(g.id).now.group?.members.count
        if n == nil { n = (try? await group(g.id))?.members.count }
        return (g.name, n ?? 0)
    }
}

/// Invite codes: six letters without I and O (the server's alphabet), typed in any case; or the long token from a link.
public enum InviteCode {
    public static let letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    public static func clean(_ s: String) -> String { String(s.uppercased().filter { letters.contains($0) }.prefix(6)) }
    public static func isCode(_ s: String) -> Bool { s.count == 6 && s.allSatisfy { letters.contains($0) } }
    /// The long token of an invite link (/j/<token>): it cannot be guessed, so it is not typed.
    public static func isToken(_ s: String) -> Bool { (32...64).contains(s.count) && s.allSatisfy(isTokenChar) }

    static func isTokenChar(_ c: Character) -> Bool { c.isASCII && (c.isLetter || c.isNumber || c == "_" || c == "-") }

    /// A pasted invite link, as the site makes them: /j/<token> (the token) or /join/<code> (the six letters); nil
    /// for anything else, which is then typed as letters.
    public static func fromLink(_ s: String) -> String? {
        if let r = s.range(of: "/j/") {
            let tail = s[r.upperBound...].prefix(while: isTokenChar)
            if (32...64).contains(tail.count) { return String(tail) }
        }
        if let r = s.range(of: "/join/") {
            let tail = s[r.upperBound...].prefix(while: { $0.isASCII && ($0.isLetter || $0 == "-") })
            if (6...9).contains(tail.count) { let c = clean(String(tail)); return isCode(c) ? c : nil }
        }
        return nil
    }

    /// How the invite came (group_join's `via`): the long token is a link, six letters were typed.
    public static func via(_ code: String) -> String { code.trimmingCharacters(in: .whitespaces).count > 8 ? "link" : "code" }

    /// The link the invite card shares (/j/<token>; it opens the app, or the site).
    public static func link(_ token: String) -> String { "https://gudauri-ski-trip.vercel.app/j/\(token)" }
    /// The link as shared from the app (copied, sent): it says it came from the app, as Android's Route.SHARED.
    public static func shared(_ token: String) -> String { link(token) + "?utm_source=app&utm_medium=share" }
}
