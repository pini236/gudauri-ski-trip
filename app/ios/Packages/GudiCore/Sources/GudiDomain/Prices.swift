import Foundation

/// One ticket of the table: [days] and the foreign visitor's price in whole GEL for an adult and a child.
public struct Pass: Sendable, Equatable {
    public let id: String
    public let days: Int
    public let adult: Int
    public let child: Int
    public let scope: String
    public let named: Bool
}

/// What something costs: the sum, and the tickets it is made of, in days.
public struct Quote: Sendable, Equatable {
    public let total: Int
    public let parts: [Int]
}

/// The ski-pass prices (round 40, approved 18.11.2026): site/data/prices.json, the resort's own price list with its source
/// and the date it was checked. GEL only. Past `until` it is "no information" (never an old price shown as current). The
/// rule for a number of days that is not a ticket is the shared one (tools/check-skipass.py, the cases
/// tools/fixtures/skipass-calc.json): the cheapest way to cover it with at most two tickets, a tie to the bigger ticket
/// first, up to `maxDays`, otherwise no price. Same as Android's skipass/Prices.kt.
public struct Prices: Sendable {
    public static let feature = "skipass"
    public static let maxDays = 14

    public let passes: [Pass]
    public let until: Day
    public let checked: Day
    public let src: String
    public let url: String
    public let nightAdult: Int?
    public let nightChild: Int?
    public let childFrom: Int
    public let childTo: Int
    public let freeUnder: Int?
    public let deposit: Int?
    public let rules: [String]
    public let buyIos: String?
    public let buyAndroid: String?

    /// False from the day after `until`: the screen says there is no information.
    public func valid(_ today: Day) -> Bool { !(until < today) }

    public func price(days: Int, child: Bool) -> Quote? {
        var table: [Int: Int] = [:]
        for p in passes { table[p.days] = child ? p.child : p.adult }
        if let t = table[days] { return Quote(total: t, parts: [days]) }
        if days < 1 || days > Prices.maxDays { return nil }
        let keys = table.keys.sorted(by: >)
        var best: Quote?
        for i in keys.indices {
            for j in i..<keys.count {
                let a = keys[i], b = keys[j]
                if a + b != days { continue }
                let total = table[a]! + table[b]!
                if best == nil || total < best!.total { best = Quote(total: total, parts: [a, b]) }
            }
        }
        return best
    }

    /// The day ticket's price, for "daily × n".
    public func daily(child: Bool) -> Int? { passes.first { $0.days == 1 }.map { child ? $0.child : $0.adult } }

    // the only store links this screen opens, as the build check (tools/check-skipass.py) holds them: no parameters but `hl`
    private static let apple = "^https://apps\\.apple\\.com/[A-Za-z0-9_./-]+(\\?hl=[A-Za-z-]+)?$"
    private static let play = "^https://play\\.google\\.com/store/apps/details\\?id=[A-Za-z0-9_.]+(&hl=[A-Za-z-]+)?$"

    public static func appleLink(_ s: String?) -> String? { s.flatMap { $0.range(of: apple, options: .regularExpression) != nil ? $0 : nil } }
    public static func playLink(_ s: String?) -> String? { s.flatMap { $0.range(of: play, options: .regularExpression) != nil ? $0 : nil } }

    /// The file, or nil when it is not the structure this app knows (a higher schema, another currency or audience, a broken file).
    public static func parse(_ data: Data?) -> Prices? {
        guard let data, let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              (o["schema"] as? Int) == 1, (o["currency"] as? String) == "GEL", (o["audience"] as? String) == "foreign",
              let raw = o["passes"] as? [[String: Any]] else { return nil }
        var ps: [Pass] = []
        for p in raw {
            guard let id = p["id"] as? String, let d = p["days"] as? Int, let a = p["adult"] as? Int, let c = p["child"] as? Int,
                  d >= 1, a >= 1, c >= 1 else { return nil }
            ps.append(Pass(id: id, days: d, adult: a, child: c, scope: p["scope"] as? String ?? "", named: p["named"] as? Bool ?? false))
        }
        let days = ps.map(\.days)
        guard !ps.isEmpty, days == Array(Set(days)).sorted() else { return nil }
        guard let until = Season.localDate(o["until"] as? String), let checked = Season.localDate(o["checked"] as? String),
              let src = o["src"] as? String, let url = o["url"] as? String, url.hasPrefix("https://") else { return nil }
        let night = o["night"] as? [String: Any]
        let kid = o["child"] as? [Int]
        let buy = o["buy"] as? [String: Any]
        func pos(_ v: Any?) -> Int? { (v as? Int).flatMap { $0 > 0 ? $0 : nil } }
        return Prices(passes: ps, until: until, checked: checked, src: src, url: url,
                      nightAdult: pos(night?["adult"]), nightChild: pos(night?["child"]),
                      childFrom: kid?.first ?? 6, childTo: (kid?.count ?? 0) > 1 ? kid![1] : 12,
                      freeUnder: pos(o["free_under"]), deposit: pos(o["card_deposit"]),
                      rules: (o["rules"] as? [String]) ?? [],
                      buyIos: appleLink(buy?["ios"] as? String), buyAndroid: playLink(buy?["android"] as? String))
    }
}
