import Foundation

/// The rule for an airport (K-5, decision 62; the site's /^[A-Z]{3}$/ and its trip.bad_code, Android's trip/Pickers.kt
/// Airports): three Latin letters. No free place names: the pass shows the code in giant letters.
public enum Airports {
    /// What was typed, as a code (upper case), or nil if it is not three Latin letters.
    public static func code(_ typed: String) -> String? {
        let t = typed.trimmingCharacters(in: .whitespaces).uppercased()
        guard t.unicodeScalars.count == 3, t.unicodeScalars.allSatisfy({ $0.value >= 65 && $0.value <= 90 }) else { return nil }
        return t
    }

    /// A place the form may save: from the list ("TBS · Tbilisi") or a code alone.
    public static func ok(_ place: String) -> Bool { Leg.code(place) != nil }

    /// The airports a Gudauri trip starts or ends at, and the hubs on the way (Android's AIRPORTS, in its order), with
    /// the key of each one's city name; any other airport by its three-letter code.
    public static let list: [(code: String, city: String)] = [
        ("TBS", "ticket.city_tbs"), ("KUT", "ticket.city_kut"), ("BUS", "app.city_bus"),
        ("TLV", "ticket.city_tlv"), ("ETM", "app.city_etm"),
        ("IST", "app.city_ist"), ("SAW", "app.city_ist"), ("DXB", "app.city_dxb"),
        ("SVO", "app.city_mow"), ("VKO", "app.city_mow"), ("DME", "app.city_mow"), ("LED", "app.city_led"),
        ("WAW", "app.city_waw"), ("RIX", "app.city_rix"), ("VIE", "app.city_vie"),
        ("MUC", "app.city_muc"), ("LHR", "app.city_lon"),
    ]

    /// The list's codes with [first] at the top, filtered by a search in the code or the city's name (as Android's sheet).
    public static func shown(first: [String], query: String, city: (String) -> String) -> [String] {
        var seen = Set<String>()
        let order = (first + list.map(\.code)).filter { seen.insert($0).inserted }
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return order }
        return order.filter { $0.localizedCaseInsensitiveContains(q) || city($0).localizedCaseInsensitiveContains(q) }
    }
}

/// Round 20 (decision 65), as Android's trip/PassForm.kt PassEdits: the pass is the form. One calendar, two taps (out
/// and back); each field of the pass changes only itself.
public enum PassEdits {
    /// One tap on the calendar: the first picks the way out, the second the way back; a tap before the way out, or a
    /// third, starts again.
    public static func tap(out: Day?, ret: Day?, day: Day) -> (out: Day, ret: Day?) {
        if let out, ret == nil, day >= out { return (out, day) }
        return (day, nil)
    }

    /// A new trip from the calendar: Tel Aviv to Tbilisi, as the form starts ([tlv], [tbs]: "TLV · Tel Aviv").
    public static func newTrip(out: Day, ret: Day?, tlv: String, tbs: String) -> Trip {
        Trip(Leg(out, from: tlv, to: tbs), ret.map { Leg($0, from: tbs, to: tlv) })
    }
}

extension Trip {
    /// New dates: each leg keeps its flight and times; a new return is the way out reversed; the ski days follow the
    /// flights again.
    public func withDates(_ o: Day, _ r: Day?) -> Trip {
        var t = self
        t.out.date = o
        t.ret = r.map { d in
            var l = ret ?? Leg(d, from: out.to, to: out.from)
            l.date = d
            return l
        }
        t.ski = nil
        return t
    }

    /// The way out's airports; the way back is the same reversed.
    public func withPlaces(from: String, to: String) -> Trip {
        var t = self
        t.out.from = from; t.out.to = to
        t.ret?.from = to; t.ret?.to = from
        return t
    }

    /// One leg changed.
    public func withLeg(ret isRet: Bool, _ f: (inout Leg) -> Void) -> Trip {
        var t = self
        if isRet { if var l = t.ret { f(&l); t.ret = l } } else { f(&t.out) }
        return t
    }
}
