import Foundation

/// The app's four languages (decisions 32 and 47). Hebrew is the source; English, Russian and Georgian are translations.
public enum Lang: String, CaseIterable, Sendable {
    case he, en, ru, ka

    public var rtl: Bool { self == .he }

    /// Decision 47: no choice in the app means Hebrew for a phone in Hebrew and English for every other phone, Russian
    /// and Georgian too. `preferred` is the phone's list of languages (Locale.preferredLanguages); only the first counts.
    public static func auto(_ preferred: [String]) -> Lang {
        let first = preferred.first?.lowercased() ?? ""
        return first.hasPrefix("he") || first.hasPrefix("iw") ? .he : .en
    }

    /// The CLDR plural category of `n` (i18n/strings.json, meta.plural_forms), for whole numbers.
    public func plural(_ n: Int) -> String {
        let a = abs(n)
        switch self {
        case .he: return a == 1 ? "one" : a == 2 ? "two" : "other"
        case .en, .ka: return a == 1 ? "one" : "other"
        case .ru:
            let m10 = a % 10, m100 = a % 100
            if m10 == 1 && m100 != 11 { return "one" }
            if (2...4).contains(m10) && !(12...14).contains(m100) { return "few" }
            return "many"
        }
    }
}

/// The texts of i18n/strings.json as tools/build-ios-strings.py packs them: {key: {lang: text or {form: text}}}.
/// Read through the typed accessors the same tool generates (`S`), never by a key typed by hand.
public final class Strings: @unchecked Sendable {
    public enum Entry: Decodable, Sendable {
        case text(String)
        case plural([String: String])
        public init(from d: Decoder) throws {
            let c = try d.singleValueContainer()
            if let s = try? c.decode(String.self) { self = .text(s) } else { self = .plural(try c.decode([String: String].self)) }
        }
    }

    private let lock = NSLock()
    private var table: [String: [String: Entry]] = [:]
    private var current: Lang = .he

    public static let shared = Strings()
    public init() {}

    public func load(_ data: Data) throws {
        let t = try JSONDecoder().decode([String: [String: Entry]].self, from: data)
        lock.withLock { table = t }
    }

    public var lang: Lang {
        get { lock.withLock { current } }
        set { lock.withLock { current = newValue } }
    }

    /// The text of `key` in the current language, with {name} placeholders filled; a missing translation falls back
    /// to Hebrew, the source, and a missing key shows the key (never an empty label).
    public func text(_ key: String, count: Int? = nil, _ args: [String: String] = [:]) -> String {
        let (entry, lang) = lock.withLock { () -> (Entry?, Lang) in
            let e = table[key]
            return (e?[current.rawValue] ?? e?[Lang.he.rawValue], e?[current.rawValue] != nil ? current : .he)
        }
        var s: String
        switch entry {
        case .text(let t): s = t
        case .plural(let forms):
            let n = count ?? 0
            s = forms[lang.plural(n)] ?? forms["other"] ?? ""
        case nil: return key
        }
        var all = args
        if let count, all["n"] == nil { all["n"] = String(count) }
        for (k, v) in all { s = s.replacingOccurrences(of: "{\(k)}", with: v) }
        return s
    }
}
