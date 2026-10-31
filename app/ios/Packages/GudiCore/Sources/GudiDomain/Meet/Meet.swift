import Foundation
import GudiData

/// A place to meet: one end of a lift, or the shared end of several (design round 3, M1 to M3; the site's MEET in
/// site/js/app.js; Android's meet/Meet.kt). Its `id` is the first lift's id and "b" (bottom) or "t" (top), exactly as
/// the site makes it, because a meetup in a group keeps this id (server/CONTRACT.md, meetups.station) and the site and
/// the apps show each other's.
public struct Station: Sendable, Identifiable, Equatable {
    public struct End: Sendable, Equatable {
        public let lift: String
        public let liftId: String
        public let top: Bool
    }
    public let id: String
    /// Where, in the site's metres (x east, y south).
    public let x: Double, y: Double
    /// Its height in metres, from the elevation grid.
    public let h: Int?
    public let ends: [End]

    /// The site's name: the first lift that starts here, or else the first that ends here.
    public var name: String { (ends.first { !$0.top } ?? ends[0]).lift }
    public var bottomOf: [String] { ends.filter { !$0.top }.map(\.lift) }
    public var topOf: [String] { ends.filter(\.top).map(\.lift) }
}

/// The group's three spots in one tap (M2), as on the site: the morning lift, noon up top, the end of the day.
public enum MeetPreset: String, CaseIterable, Sendable {
    case morning, noon, end
    public var lift: String { self == .end ? "New Goodaura" : "Goodaura" }
    public var top: Bool { self == .noon }
    public var time: ClockTime { switch self { case .morning: ClockTime(9, 30)!; case .noon: ClockTime(13, 0)!; case .end: ClockTime(16, 30)! } }
    /// The measurement's name (meet_pick's preset).
    public var event: String { rawValue }
}

/// How to get to a station, only from the connections in the data (M3): where from, then the runs or the lift.
/// (Round 20 took "how to get there" off the screen; the rule stays, tested as Android's.)
public struct MeetWay: Sendable, Equatable {
    public enum From: Sendable, Equatable { case run(String), topOf(String), bottomOf(String) }
    public enum Hop: Sendable, Equatable { case run(String), lift(String) }
    public let from: From
    public let hops: [Hop]
    var sameAs: String { hops.map { if case .run(let k) = $0 { "r:" + k } else if case .lift(let n) = $0 { "l:" + n } else { "" } }.joined(separator: "|") }
}

/// The stations of the mountain and what is known about each, built once from the site's data.
public struct MeetPlan: Sendable {
    public let stations: [Station]
    public let byId: [String: Station]
    private let pistes: [Piste]
    private let byKey: [String: Piste]

    /// Ends of lifts closer than this are one station (shared top stations).
    public static let mergeM = 70.0

    public init(stations: [Station], pistes: [Piste]) {
        self.stations = stations
        byId = Dictionary(stations.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        self.pistes = pistes
        byKey = Dictionary(pistes.map { ($0.key, $0) }, uniquingKeysWith: { a, _ in a })
    }

    public func preset(_ p: MeetPreset) -> Station? {
        stations.first { s in s.ends.contains { $0.lift == p.lift && $0.top == p.top } }
    }

    /// The site's routes(): runs that end at a lift starting here (with the run before them), or the lift up to here.
    public func ways(_ s: Station) -> [MeetWay] {
        var out: [MeetWay] = []
        for e in s.ends {
            if !e.top {
                for p in pistes where p.named && p.toLifts.contains(e.lift) {
                    let before = p.fromPistes.compactMap { byKey[$0] }.first { $0.named && $0.key != p.key }
                    out.append(MeetWay(from: before.map { .run($0.key) } ?? .topOf(p.key),
                                       hops: ([before, p] as [Piste?]).compactMap { $0 }.map { .run($0.key) }))
                }
            } else {
                out.append(MeetWay(from: .bottomOf(e.lift), hops: [.lift(e.lift)]))
            }
        }
        var seen = Set<String>()
        return Array(out.filter { seen.insert($0.sameAs).inserted }.prefix(4))
    }

    /// Both ends of every named lift on the main side that runs, merged when close, the lower end "b".
    public static func build(_ runs: RunsAndLifts, _ relief: Relief) -> MeetPlan {
        struct Open { let id: String; let x: Double; let y: Double; let h: Int; var ends: [Station.End] }
        var st: [Open] = []
        for l in runs.mainLifts {
            guard !l.name.isEmpty, l.status != "inactive", l.g.count >= 2 else { continue }
            let pts = Geo.line(l.g)
            let n = pts.count
            var lo = (pts[0], pts[1]), hi = (pts[n - 2], pts[n - 1])
            if relief.elev(lo.0, lo.1) > relief.elev(hi.0, hi.1) { swap(&lo, &hi) }
            for (q, top) in [(lo, false), (hi, true)] {
                if let i = st.firstIndex(where: { hypot($0.x - q.0, $0.y - q.1) < mergeM }) {
                    st[i].ends.append(.init(lift: l.name, liftId: l.id, top: top)); continue
                }
                st.append(Open(id: l.id + (top ? "t" : "b"), x: q.0, y: q.1, h: Int((relief.elev(q.0, q.1) + 0.5).rounded(.down)),
                               ends: [.init(lift: l.name, liftId: l.id, top: top)]))
            }
        }
        return MeetPlan(stations: st.map { Station(id: $0.id, x: $0.x, y: $0.y, h: $0.h, ends: $0.ends) }, pistes: runs.pistes)
    }
}

/// The rules of the meeting point that are not drawing: the times on offer, the days, the countdown, the link.
public enum Meet {
    /// Gudauri's clock (UTC+4, no summer time): the site counts down in it, and a meetup is saved with it.
    public static let gudauriOffsetS = 4 * 3600
    public static let times: [ClockTime] = [ClockTime(9, 30)!, ClockTime(11, 0)!, ClockTime(12, 30)!, ClockTime(13, 30)!, ClockTime(15, 0)!, ClockTime(16, 30)!]
    public static let defaultTime = ClockTime(12, 30)!
    public static let siteHost = "gudauri-ski-trip.vercel.app"
    /// What a link the app shares carries (docs/GROWTH.md), as Android's Route.SHARED.
    public static let shared = "utm_source=app&utm_medium=share"

    /// Today in Gudauri.
    public static func gudauriDay(_ nowMs: Int64) -> Day {
        let s = nowMs / 1000 + Int64(gudauriOffsetS)
        return Day(epochDay: Int((Double(s) / 86_400).rounded(.down)))
    }

    /// A saved meetup's card as the meeting point's route takes it ("<station>/<HHMM>/<YYYYMMDD>"), in Gudauri's
    /// clock: a tap on a meetup in the group page opens it (K-3, as Android's Meet.route and the site's group page).
    public static func card(_ station: String, atMs: Int64) -> String {
        let s = atMs / 1000 + Int64(gudauriOffsetS)
        let day = gudauriDay(atMs)
        let inDay = Int(s - Int64(day.epochDay) * 86_400)
        return "\(station)/\(pad2(inDay / 3600))\(pad2(inDay % 3600 / 60))/\(day.year)\(pad2(day.month))\(pad2(day.day))"
    }

    /// The ski days of "your trip" (up to two weeks), or, with none, the next week from today in Gudauri.
    public static func days(_ trip: Trip?, nowMs: Int64) -> [Day] {
        var a: Day, b: Day
        if let r = trip?.skiDays() { a = r.lowerBound; b = r.upperBound } else {
            a = gudauriDay(nowMs)
            if let t = trip, t.out.date > a { a = t.out.date.plus(days: 1) }
            b = a.plus(days: 6)
        }
        var out: [Day] = []
        var d = a
        while d <= b && out.count < 14 { out.append(d); d = d.plus(days: 1) }
        return out
    }

    /// The moment of a meetup, in epoch ms, on Gudauri's clock.
    public static func at(_ day: Day, _ time: ClockTime) -> Int64 {
        Int64(day.epochDay * 86_400 + time.minutes * 60 - gudauriOffsetS) * 1000
    }

    public enum Left: Equatable, Sendable {
        case passed
        case minutes(Int)
        /// hours and minutes, "1:05"
        case hours(String)
        case days(Int)
    }

    /// As the site counts (site/js/app.js countdown()): minutes under an hour, h:mm under a day, then whole days up.
    public static func left(at: Int64, now: Int64) -> Left {
        let diff = Double(at - now) / 60_000
        func round(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }
        if diff < 0 { return .passed }
        if diff < 60 { return .minutes(round(diff)) }
        if diff < 24 * 60 { let m = round(diff); return .hours("\(m / 60):\(pad2(m % 60))") }
        return .days(Int((diff / 1440).rounded(.up)))
    }

    /// "meet/158744075b/0930/20270111", the site's hash path for a meeting point.
    public static func path(_ station: String, _ time: ClockTime, _ day: Day) -> String {
        "meet/\(station)/\(pad2(time.hour))\(pad2(time.minute))/\(day.year)\(pad2(day.month))\(pad2(day.day))"
    }

    /// The site's link to this meeting point; the app reads the same link back (`MeetLink.parse`).
    public static func link(_ station: String, _ time: ClockTime, _ day: Day) -> String {
        "https://\(siteHost)/#" + path(station, time, day)
    }

    /// The same link as it is shared, saying it came from the app.
    public static func sharedLink(_ station: String, _ time: ClockTime, _ day: Day) -> String {
        "https://\(siteHost)/?\(shared)#" + path(station, time, day)
    }

    /// "0930" and "20270111" from a link, or nil when they do not read as a time and a date.
    public static func time(_ hhmm: String?) -> ClockTime? {
        guard let s = hhmm, s.count == 4, s.allSatisfy(\.isASCII), let h = Int(s.prefix(2)), let m = Int(s.suffix(2)) else { return nil }
        return ClockTime(h, m)
    }

    public static func day(_ yyyymmdd: String?) -> Day? {
        guard let s = yyyymmdd, s.count == 8, s.allSatisfy({ $0.isASCII && $0.isNumber }),
              let y = Int(s.prefix(4)), let m = Int(s.dropFirst(4).prefix(2)), let d = Int(s.suffix(2)) else { return nil }
        return Day(y, m, d)
    }
}

/// A meeting point as a link carries it: `#meet`, `#meet/<station>/<HHMM>/<YYYYMMDD>`, or the whole site URL with
/// that hash (as Android's Route.parse and Route.fromSiteLink). Also what the app's route `.meet(station:)` carries:
/// either a bare station id or "<station>/<HHMM>/<YYYYMMDD>".
public struct MeetLink: Equatable, Sendable {
    public var station: String?
    public var time: String?
    public var day: String?
    public init(station: String? = nil, time: String? = nil, day: String? = nil) { self.station = station; self.time = time; self.day = day }

    static func isStation(_ s: Substring) -> Bool {
        (1...60).contains(s.count) && s.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "_" || $0 == "." || $0 == "-") }
    }
    static func digits(_ s: Substring, _ n: Int) -> Bool { s.count == n && s.allSatisfy { $0.isASCII && $0.isNumber } }

    /// A hash path ("#meet/…" or "meet/…"), or nil for anything that is not a meeting point.
    public static func parse(path raw: String) -> MeetLink? {
        var p = raw.trimmingCharacters(in: .whitespaces)
        if p.hasPrefix("#") { p.removeFirst() }
        while p.hasSuffix("/") { p.removeLast() }
        let parts = p.split(separator: "/", omittingEmptySubsequences: false)
        guard parts.first == "meet" else { return nil }
        if parts.count == 1 { return MeetLink() }
        if parts.count == 4, isStation(parts[1]), digits(parts[2], 4), digits(parts[3], 8) {
            return MeetLink(station: String(parts[1]), time: String(parts[2]), day: String(parts[3]))
        }
        return nil
    }

    /// A link to the site (https://gudauri-ski-trip.vercel.app/#meet/…, with or without the query of a shared link).
    public static func parse(url: String) -> MeetLink? {
        guard let u = URLComponents(string: url), u.scheme == "https", u.host == Meet.siteHost else { return nil }
        let path = u.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard path.isEmpty || path == "index.html", let f = u.fragment else { return nil }
        return parse(path: f)
    }

    /// The route's argument: a bare station id, or "<station>/<HHMM>/<YYYYMMDD>".
    public static func parse(route arg: String?) -> MeetLink {
        guard let arg, !arg.isEmpty else { return MeetLink() }
        if let l = parse(path: "meet/" + arg) { return l }
        return isStation(Substring(arg)) ? MeetLink(station: arg) : MeetLink()
    }
}

/// The group's meetups remind a quarter of an hour before (Q8), from the phone itself (Android's meet/Reminders.kt):
/// the rule of which ones, without the system's notifications (the app's Meet/Reminders.swift sets those).
public enum MeetReminders {
    public static let beforeMin = 15

    /// One meetup to remind of: where (the station's name), the group, its time in Gudauri, and the page it opens.
    public struct Item: Equatable, Sendable, Codable {
        public let id: String
        /// epoch ms
        public let at: Int64
        public let place: String
        public let group: String
        public let time: String
        /// The meeting point's path ("meet/<station>/<HHMM>/<YYYYMMDD>").
        public let open: String
        public init(id: String, at: Int64, place: String, group: String, time: String, open: String) {
            self.id = id; self.at = at; self.place = place; self.group = group; self.time = time; self.open = open
        }
        /// When it rings, epoch ms.
        public var ring: Int64 { at - Int64(beforeMin * 60_000) }
    }

    /// The ones to remind of: not turned off, and their quarter of an hour before is still ahead.
    public static func due(_ items: [Item], off: Set<String>, now: Int64) -> [Item] {
        items.filter { !off.contains($0.id) && $0.ring > now }
    }
}
