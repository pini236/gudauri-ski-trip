import Foundation

/// A calendar day and a clock time with no time zone, like Kotlin's LocalDate and LocalTime on Android. The trip's
/// rules (docs/PARITY.md, A-1, A-2) count in the phone's own days, so plain numbers keep them identical on every
/// platform, without Calendar or Foundation differences between iOS and Linux.
public struct Day: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int, month: Int, day: Int

    /// nil for a day that does not exist (31 February).
    public init?(_ year: Int, _ month: Int, _ day: Int) {
        guard (1...12).contains(month), day >= 1, day <= Day.length(year, month) else { return nil }
        self.year = year; self.month = month; self.day = day
    }

    private init(unchecked y: Int, _ m: Int, _ d: Int) { year = y; month = m; day = d }

    static func length(_ y: Int, _ m: Int) -> Int {
        switch m {
        case 2: return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }

    /// Days since 1970-01-01 (Howard Hinnant's days_from_civil).
    public var epochDay: Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    public init(epochDay z0: Int) {
        let z = z0 + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        self.init(unchecked: yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }

    public func plus(days n: Int) -> Day { Day(epochDay: epochDay + n) }

    /// 2027-01-10, as saved and as on the site.
    public var iso: String { "\(year)-\(pad2(month))-\(pad2(day))" }

    public init?(iso s: String) {
        let p = s.split(separator: "-")
        guard p.count == 3, p[0].count == 4, p[1].count == 2, p[2].count == 2,
              let y = Int(p[0]), let m = Int(p[1]), let d = Int(p[2]) else { return nil }
        self.init(y, m, d)
    }

    public var description: String { iso }
    public static func < (a: Day, b: Day) -> Bool { a.epochDay < b.epochDay }
}

public struct ClockTime: Hashable, Comparable, Sendable {
    public let hour: Int, minute: Int

    public init?(_ hour: Int, _ minute: Int) {
        guard (0...23).contains(hour), (0...59).contains(minute) else { return nil }
        self.hour = hour; self.minute = minute
    }

    public static let noon = ClockTime(12, 0)!
    public var minutes: Int { hour * 60 + minute }
    /// 01:35
    public var text: String { "\(pad2(hour)):\(pad2(minute))" }

    public init?(text s: String) {
        let p = s.split(separator: ":")
        guard p.count == 2, let h = Int(p[0]), let m = Int(p[1]) else { return nil }
        self.init(h, m)
    }

    public static func < (a: ClockTime, b: ClockTime) -> Bool { a.minutes < b.minutes }
}

/// A moment in the phone's own time, to the second.
public struct LocalMoment: Sendable {
    public let day: Day
    public let seconds: Int
    public init(_ day: Day, _ hour: Int, _ minute: Int, _ second: Int = 0) {
        self.day = day; seconds = hour * 3600 + minute * 60 + second
    }
}

func pad2(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

/// "10.1", as the site writes short dates.
public func shortDate(_ d: Day) -> String { "\(d.day).\(d.month)" }

/// "11–14.1", or "30.12–2.1" across a month, or "11.1" for one day (Android's home/Pass.kt dayRange).
public func dayRange(_ r: ClosedRange<Day>) -> String {
    if r.lowerBound == r.upperBound { return shortDate(r.lowerBound) }
    if r.lowerBound.month == r.upperBound.month { return "\(r.lowerBound.day)–\(shortDate(r.upperBound))" }
    return "\(shortDate(r.lowerBound))–\(shortDate(r.upperBound))"
}

extension LocalMoment {
    /// Now on the phone's own clock, as Android's LocalDateTime.now() for the trip's countdown.
    public static func now(_ date: Date = Date(), calendar: Calendar = .current) -> LocalMoment {
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        return LocalMoment(Day(c.year!, c.month!, c.day!)!, c.hour!, c.minute!, c.second!)
    }
}
