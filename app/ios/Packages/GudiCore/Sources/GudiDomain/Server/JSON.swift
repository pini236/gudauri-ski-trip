import Foundation

/// A JSON value, as Android's org.json is used in server/: the server's answers and the bodies we send, read loosely
/// (a field the app does not know is ignored, server/CONTRACT.md, "כללי שינוי" 2). Sendable, so it crosses actors.
public enum JSON: Sendable, Equatable, Codable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSON])
    case object([String: JSON])

    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let b = try? c.decode(Bool.self) { self = .bool(b) }
        else if let n = try? c.decode(Double.self) { self = .number(n) }
        else if let s = try? c.decode(String.self) { self = .string(s) }
        else if let a = try? c.decode([JSON].self) { self = .array(a) }
        else { self = .object(try c.decode([String: JSON].self)) }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .bool(let b): try c.encode(b)
        case .number(let n):
            // whole numbers as integers ("score": 12, not 12.0)
            if n.rounded() == n, abs(n) < 1e15 { try c.encode(Int64(n)) } else { try c.encode(n) }
        case .string(let s): try c.encode(s)
        case .array(let a): try c.encode(a)
        case .object(let o): try c.encode(o)
        }
    }

    /// Parse; nil for anything that is not JSON (a proxy's page, a cut answer).
    public static func parse(_ data: Data) -> JSON? {
        guard !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(JSON.self, from: data)
    }

    public static func parse(_ text: String) -> JSON? { parse(Data(text.utf8)) }

    public var data: Data {
        let e = JSONEncoder()
        e.outputFormatting = .sortedKeys
        return (try? e.encode(self)) ?? Data("null".utf8)
    }

    public var text: String { String(decoding: data, as: UTF8.self) }

    // MARK: reading

    public subscript(key: String) -> JSON? {
        if case .object(let o) = self { return o[key] }
        return nil
    }

    public var object: [String: JSON]? { if case .object(let o) = self { return o }; return nil }
    public var array: [JSON]? { if case .array(let a) = self { return a }; return nil }
    public var isNull: Bool { self == .null }

    /// A string, or nil for null, a missing field or an empty one (Android's `str()`: blank is none).
    public func str(_ key: String) -> String? {
        guard case .string(let s)? = self[key], !s.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return s
    }

    public func string(_ key: String) -> String {
        switch self[key] {
        case .string(let s)?: return s
        case .number(let n)?: return n.rounded() == n ? String(Int64(n)) : String(n)
        case .bool(let b)?: return b ? "true" : "false"
        default: return ""
        }
    }

    public func int(_ key: String) -> Int? {
        switch self[key] {
        case .number(let n)?: return Int(n)
        case .string(let s)?: return Int(s)
        default: return nil
        }
    }

    public func bool(_ key: String) -> Bool {
        switch self[key] {
        case .bool(let b)?: return b
        case .string(let s)?: return s == "true"
        case .number(let n)?: return n != 0
        default: return false
        }
    }

    public func objects(_ key: String) -> [JSON] { (self[key]?.array ?? []).filter { $0.object != nil } }

    /// The elements of an array that are objects.
    public var objects: [JSON] { (array ?? []).filter { $0.object != nil } }

    // MARK: building

    public static func obj(_ pairs: [String: JSON?]) -> JSON {
        var o: [String: JSON] = [:]
        for (k, v) in pairs { o[k] = v ?? .null }
        return .object(o)
    }

    public static func str(_ s: String?) -> JSON { s.map { .string($0) } ?? .null }
    public static func int(_ n: Int?) -> JSON { n.map { .number(Double($0)) } ?? .null }

    /// A copy with one more field.
    public func with(_ key: String, _ value: JSON) -> JSON {
        var o = object ?? [:]
        o[key] = value
        return .object(o)
    }
}

extension JSON: ExpressibleByStringLiteral, ExpressibleByBooleanLiteral, ExpressibleByIntegerLiteral, ExpressibleByNilLiteral {
    public init(stringLiteral v: String) { self = .string(v) }
    public init(booleanLiteral v: Bool) { self = .bool(v) }
    public init(integerLiteral v: Int) { self = .number(Double(v)) }
    public init(nilLiteral: ()) { self = .null }
}

/// Moments as the server writes them ("2027-01-10T08:30:00+00:00", sometimes with fractions) and as we send them.
public enum ISOTime {
    public static func parse(_ s: String) -> Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        if let d = f.date(from: s) { return d }
        // "2027-01-10T08:30:00" with no zone: UTC, as Postgres' timestamp without zone
        f.formatOptions = [.withFullDate, .withTime, .withColonSeparatorInTime, .withDashSeparatorInDate]
        f.timeZone = TimeZone(secondsFromGMT: 0)
        return f.date(from: s)
    }

    /// "2027-01-11T05:30:00Z", as Android's Instant.toString() without fractions.
    public static func text(_ d: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.string(from: d)
    }
}
