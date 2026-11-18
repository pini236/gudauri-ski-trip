import Foundation
import GudiData

/// The countdown to the season's opening (round 39, approved 18.11.2026; the same rules as Android's season/Season.kt and the
/// shared cases tools/fixtures/season-countdown.json). The date is `season.open` in resorts.json: a LOCAL date in the
/// resort's time zone, never read from the phone's clock, so a phone in another zone counts the same days as the site.
/// In order: a fresh lift report is the lift board; no readable date is no countdown; days before the date is the
/// countdown, the day itself is "opening day", and after it, with no report, "no information" (never "delayed").
public struct Season: Sendable, Equatable {
    public enum Kind: Sendable, Equatable { case none, countdown, openingDay, noInfo, live }

    public struct State: Sendable, Equatable {
        public let kind: Kind
        /// Set for the countdown (above 0) and for opening day (0).
        public let days: Int?
        /// Only while counting down.
        public let postponed: Bool
    }

    public let open: Day
    public let announced: Day?
    public let src: String?
    public let url: String?
    public let updated: Day?
    public let tz: TimeZone

    public static let defaultTZ = TimeZone(identifier: "Asia/Tbilisi")!

    /// A real calendar date written YYYY-MM-DD, otherwise nil ("20.12.2026" and "2026-12-20T09:00" are not).
    public static func localDate(_ s: String?) -> Day? { s.flatMap { Day(iso: $0) } }

    /// The resort's `season`; nil when it has none or `open` is not a readable date (then no countdown).
    public static func from(_ raw: Resorts.SeasonRaw?, tz: String?) -> Season? {
        guard let raw, let open = localDate(raw.open) else { return nil }
        return Season(open: open, announced: localDate(raw.announced), src: raw.src.flatMap { $0.isEmpty ? nil : $0 },
                      url: raw.url.flatMap { $0.hasPrefix("https://") ? $0 : nil }, updated: localDate(raw.updated),
                      tz: tz.flatMap { TimeZone(identifier: $0) } ?? defaultTZ)
    }

    public static func parse(_ resorts: Resorts?, resort: String = "gudauri") -> Season? {
        guard let resorts else { return nil }
        return from(resorts.season(of: resort), tz: resorts.tz(of: resort))
    }

    /// Today in the resort's own calendar.
    public static func today(_ date: Date, tz: TimeZone) -> Day {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        let c = cal.dateComponents([.year, .month, .day], from: date)
        return Day(c.year!, c.month!, c.day!)!
    }

    public func today(_ date: Date) -> Day { Season.today(date, tz: tz) }

    /// The rules above. [season] is nil when the file has none; [seenOpen] is the date this phone saw before.
    public static func decide(_ season: Season?, today: Day, fresh: Bool, seenOpen: Day?) -> State {
        if fresh { return State(kind: .live, days: nil, postponed: false) }
        guard let season else { return State(kind: .none, days: nil, postponed: false) }
        let days = season.open.epochDay - today.epochDay
        if days > 0 { return State(kind: .countdown, days: days, postponed: seenOpen.map { $0 < season.open } ?? false) }
        if days == 0 { return State(kind: .openingDay, days: 0, postponed: false) }
        return State(kind: .noInfo, days: nil, postponed: false)
    }

    /// How far the cabin has gone up its line, from the day the date was announced to the date itself.
    public func progress(_ s: State, today: Day) -> Double {
        if s.kind == .openingDay { return 1 }
        guard let from = announced, from < open else { return 0 }
        let p = Double(today.epochDay - from.epochDay) / Double(open.epochDay - from.epochDay)
        return min(0.98, max(0.02, p))
    }
}
