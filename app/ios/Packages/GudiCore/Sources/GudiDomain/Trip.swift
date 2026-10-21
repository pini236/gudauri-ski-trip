import Foundation

/// "Your trip" (decision 27, screens H1 to H4): one person's own flights, kept only on this phone. The same rules as
/// Android (`trip/Trip.kt`) and the site (`MYTRIP` in site/js/app.js); the tests check them against the same cases.
public struct Leg: Equatable, Sendable {
    public var date: Day
    public var flight: String
    public var from: String
    public var to: String
    public var departs: ClockTime?
    public var arrives: ClockTime?

    public init(_ date: Day, flight: String = "", from: String = "", to: String = "", departs: ClockTime? = nil, arrives: ClockTime? = nil) {
        self.date = date; self.flight = flight; self.from = from; self.to = to; self.departs = departs; self.arrives = arrives
    }

    /// "TLV · Tel Aviv" (or "TLV Tel Aviv") -> TLV and Tel Aviv; a place typed without an airport code has none.
    public var fromCode: String? { Leg.code(from) }
    public var toCode: String? { Leg.code(to) }
    public var fromCity: String { Leg.city(from) }
    public var toCity: String { Leg.city(to) }

    // three letters alone or before "·" ("tlv", "TLV · Tel Aviv"), or three capitals before a space ("TLV Tel Aviv");
    // "Tel Aviv" is a city, not the code TEL
    static func code(_ s: String) -> String? {
        let t = Array(s.drop(while: { $0 == " " }))
        guard t.count >= 3, t[0..<3].allSatisfy({ $0.isASCII && $0.isLetter }) else { return nil }
        let three = String(t[0..<3])
        let rest = String(t[3...])
        let afterSpaces = rest.drop(while: { $0 == " " })
        if afterSpaces.isEmpty || afterSpaces.first == "·" { return three.uppercased() }
        if rest.first == " ", three.allSatisfy({ $0.isUppercase }) { return three }
        return nil
    }

    static func city(_ s: String) -> String {
        let t = s.trimmingCharacters(in: .whitespaces)
        guard code(s) != nil else { return t }
        return String(t.dropFirst(3)).drop(while: { $0 == " " || $0 == "·" }).trimmingCharacters(in: .whitespaces)
    }
}

public struct Trip: Equatable, Sendable {
    public var out: Leg
    public var ret: Leg?
    /// Ski days set by hand ("שינוי" in H2); nil means taken from the flights.
    public var ski: ClosedRange<Day>?

    public init(_ out: Leg, _ ret: Leg? = nil, ski: ClosedRange<Day>? = nil) {
        self.out = out; self.ret = ret; self.ski = ski
    }

    public func skiDays() -> ClosedRange<Day>? { ski ?? flightSkiDays() }

    /// Full ski days, as on the site (MYTRIP.skiAuto): from the day after landing (the same day if you land before
    /// 12:00) to the day before the return flight (the same day if it leaves at 18:00 or later). An overnight flight
    /// lands the next day. Nil when there is no return, or no full day.
    public func flightSkiDays() -> ClosedRange<Day>? {
        guard let r = ret else { return nil }
        let landed: Day
        if let dep = out.departs, let arr = out.arrives, arr < dep { landed = out.date.plus(days: 1) } else { landed = out.date }
        let first = (out.arrives.map { $0 < .noon } ?? false) ? landed : landed.plus(days: 1)
        let last = (r.departs.map { $0 >= ClockTime(18, 0)! } ?? false) ? r.date : r.date.plus(days: -1)
        return first <= last ? first...last : nil
    }

    public func skiDayCount() -> Int {
        guard let d = skiDays() else { return 0 }
        return d.upperBound.epochDay - d.lowerBound.epochDay + 1
    }

    /// Days until the outbound flight, as on the site (MYTRIP.days): to the midnight that starts the flight's day, in
    /// the phone's time, rounded up. 0 from that midnight on ("on the way").
    public func daysToFlight(_ now: LocalMoment) -> Int {
        let seconds = (out.date.epochDay - now.day.epochDay) * 86_400 - now.seconds
        return seconds <= 0 ? 0 : (seconds + 86_399) / 86_400
    }

    /// Where the trip stands today, as on the site (S-35, A-43; decision 58).
    public enum Stage: Equatable, Sendable {
        /// Before the first ski day: the countdown to the flight (0 from its day on, "on the way").
        case before(days: Int)
        /// On a ski day, n of m; from the last ski day to the return, m of m.
        case skiDay(Int, of: Int)
        /// From the day after the return (or after the last ski day, when there is no return): no count at all.
        case over
    }

    public func stage(_ now: LocalMoment) -> Stage {
        let today = now.day
        let days = skiDays()
        if let end = ret?.date ?? days?.upperBound, today > end { return .over }
        if let days, today >= days.lowerBound {
            let m = skiDayCount()
            return .skiDay(min(today.epochDay - days.lowerBound.epochDay + 1, m), of: m)
        }
        return .before(days: daysToFlight(now))
    }
}

// MARK: - Saved as JSON, in the same format as Android, so a trip moves between them unchanged

extension Trip {
    struct Saved: Codable {
        var v: Int?
        var out: SavedLeg
        var ret: SavedLeg?
        /// How the parallel Android build of 2.10.2026 named the return; read too, like Android.
        var back: SavedLeg?
        var ski: SavedSki?
    }

    struct SavedLeg: Codable {
        var date: String
        var flight: String?
        var number: String?
        var from: String?
        var to: String?
        var departs: String?
        var arrives: String?
    }

    struct SavedSki: Codable { var from: String; var to: String }

    public func json() -> Data {
        func leg(_ l: Leg) -> SavedLeg {
            SavedLeg(date: l.date.iso, flight: l.flight, from: l.from, to: l.to, departs: l.departs?.text ?? "", arrives: l.arrives?.text ?? "")
        }
        let s = Saved(v: 1, out: leg(out), ret: ret.map(leg), ski: ski.map { SavedSki(from: $0.lowerBound.iso, to: $0.upperBound.iso) })
        let e = JSONEncoder(); e.outputFormatting = .sortedKeys
        return (try? e.encode(s)) ?? Data()
    }

    /// A saved trip, or nil for anything odd (a newer format, a damaged file): the home page then invites to add one.
    public static func from(json data: Data?) -> Trip? {
        guard let data, let s = try? JSONDecoder().decode(Saved.self, from: data), (s.v ?? 1) <= 1 else { return nil }
        func leg(_ l: SavedLeg) -> Leg? {
            guard let d = Day(iso: l.date) else { return nil }
            let flight = (l.flight ?? "").trimmingCharacters(in: .whitespaces).isEmpty ? (l.number ?? "") : (l.flight ?? "")
            return Leg(d, flight: flight, from: l.from ?? "", to: l.to ?? "",
                       departs: (l.departs ?? "").isEmpty ? nil : ClockTime(text: l.departs!),
                       arrives: (l.arrives ?? "").isEmpty ? nil : ClockTime(text: l.arrives!))
        }
        guard let out = leg(s.out) else { return nil }
        var ski: ClosedRange<Day>?
        if let k = s.ski, let a = Day(iso: k.from), let b = Day(iso: k.to), a <= b { ski = a...b }
        return Trip(out, (s.ret ?? s.back).flatMap(leg), ski: ski)
    }
}
