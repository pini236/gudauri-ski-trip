import Foundation

/// The moments engine (tools/dayrec/moments.py; docs/AI-PRODUCT.md opportunity 1): from a day's final analysis and the
/// skier's own history, the moments of the day (the fastest moment, the longest descent, the first red, new runs,
/// records, the longest queue, the drop against known heights, falls, off-piste). It writes no text: each moment is
/// a key and parameters, and the words come from i18n/strings.json (moment.<key>). On the phone, with no network and
/// no language model. Pure, so it matches tools/fixtures/dayrec-moments.json exactly.
extension DayRec {
    /// The `moments` section of dayrec-params.json.
    public struct MomentConfig: Decodable, Sendable, Equatable {
        public struct Ref: Decodable, Sendable, Equatable { public let id: String; public let m: Double }
        public let version: Int
        public let storyMax: Int, storyPerKey: Int
        public let colorMinM: Double
        public let colorsFirst: [String], colorsAll: [String]
        public let records: [String]
        public let queueMinS: Double
        public let dropMinM: Double
        public let dropTimes: [Double]
        public let dropRefs: [Ref]
        public let priority: [String: Double]
        public let shareExclude: [String]

        enum CodingKeys: String, CodingKey {
            case version, records, priority
            case storyMax = "story_max", storyPerKey = "story_per_key", colorMinM = "color_min_m"
            case colorsFirst = "colors_first", colorsAll = "colors_all", queueMinS = "queue_min_s", dropMinM = "drop_min_m"
            case dropTimes = "drop_times", dropRefs = "drop_refs", shareExclude = "share_exclude"
        }
    }

    /// A moment's parameter: numbers, names and flags only.
    public enum Value: Codable, Equatable, Sendable {
        case null, bool(Bool), num(Double), str(String), list([Value]), obj([String: Value])

        static func int(_ v: Int) -> Value { .num(Double(v)) }
        static func names(_ v: [String]) -> Value { .list(v.map { .str($0) }) }
        static func opt(_ v: Double?) -> Value { v.map { .num($0) } ?? .null }

        public init(from d: Decoder) throws {
            let c = try d.singleValueContainer()
            if c.decodeNil() { self = .null }
            else if let b = try? c.decode(Bool.self) { self = .bool(b) }
            else if let n = try? c.decode(Double.self) { self = .num(n) }
            else if let s = try? c.decode(String.self) { self = .str(s) }
            else if let a = try? c.decode([Value].self) { self = .list(a) }
            else { self = .obj(try c.decode([String: Value].self)) }
        }

        public func encode(to e: Encoder) throws {
            var c = e.singleValueContainer()
            switch self {
            case .null: try c.encodeNil()
            case .bool(let b): try c.encode(b)
            case .num(let n): if n == n.rounded() && abs(n) < 1e15 { try c.encode(Int64(n)) } else { try c.encode(n) }
            case .str(let s): try c.encode(s)
            case .list(let a): try c.encode(a)
            case .obj(let o): try c.encode(o)
            }
        }
    }

    public struct Moment: Codable, Equatable, Sendable {
        public var key: String
        public var t: Int64
        /// The app shows text variant `seed % (variants of the key)`, so both apps pick the same one.
        public var seed: Int
        public var p: [String: Value]
    }

    /// The skier's history on the phone, never with a position: the first time of each run and colour, and the bests.
    public struct History: Codable, Equatable, Sendable {
        public static let version = 1
        public var v = History.version
        public var days = 0
        public var runs: [String: Int64] = [:]
        public var colors: [String: Int64] = [:]
        public var best: [String: Double] = [:]
        public init() {}
    }

    public struct Moments: Codable, Equatable, Sendable {
        public var moments: Int
        /// By time.
        public var items: [Moment]
        /// Indexes into items, by time.
        public var story: [Int]
        public var share: Int?
        /// The history after this day.
        public var history: History

        public func encode(to e: Encoder) throws {
            var c = e.container(keyedBy: CodingKeys.self)
            try c.encode(moments, forKey: .moments); try c.encode(items, forKey: .items); try c.encode(story, forKey: .story)
            try c.encode(share, forKey: .share)   // null, not missing
            try c.encode(history, forKey: .history)
        }
    }

    /// crc32 of "<key>|<t>", 31 bits.
    public static func seed(_ key: String, _ t: Int64) -> Int {
        var c: UInt32 = 0xFFFF_FFFF
        for b in "\(key)|\(t)".utf8 {
            c ^= UInt32(b)
            for _ in 0..<8 { c = c & 1 != 0 ? (c >> 1) ^ 0xEDB8_8320 : c >> 1 }
        }
        return Int(~c & 0x7FFF_FFFF)
    }

    static func descents(_ r: Analysis) -> [Segment] { r.segments.filter { $0.type == "descent" } }

    /// What the skier was on at time t.
    static func legAt(_ r: Analysis, _ t: Int64) -> Leg? {
        for s in descents(r) { for l in s.legs ?? [] where l.t0 <= t && t <= l.t1 { return l } }
        return nil
    }

    static func whereAt(_ l: Leg?) -> [String: Value] {
        guard let l else { return ["kind": .str("unmatched")] }
        var o: [String: Value] = ["kind": .str(l.kind)]
        if l.kind == "run" { o["runs"] = .names(l.runs) }
        return o
    }

    /// The runs of a descent by the distance on each (from legs on one run only), longest first.
    static func mainRuns(_ s: Segment, _ n: Int = 3) -> [String] {
        var d: [String: Double] = [:]
        for l in s.legs ?? [] where l.kind == "run" && l.runs.count == 1 { d[l.runs[0], default: 0] += l.distM }
        return Array(d.sorted { $0.value != $1.value ? $0.value > $1.value : pyLess($0.key, $1.key) }.map(\.key).prefix(n))
    }

    /// The named runs skied today, with the time of the first leg on that one run of at least color_min_m.
    static func done(_ r: Analysis, _ runs: [String: RunInfo], _ M: MomentConfig) -> [(k: String, t: Int64)] {
        var out: [String: Int64] = [:]
        for s in descents(r) {
            for l in s.legs ?? [] where l.kind == "run" && l.runs.count == 1 && l.distM >= M.colorMinM {
                let k = l.runs[0]
                if runs[k]?.named == true && out[k] == nil { out[k] = l.t0 }
            }
        }
        return out.map { ($0.key, $0.value) }.sorted { $0.t != $1.t ? $0.t < $1.t : pyLess($0.k, $1.k) }
    }

    static func dayBests(_ r: Analysis) -> [String: Double?] {
        let ds = descents(r), st = r.stats
        return [
            "max_kmh": st?.maxKmh,
            "drop_m": ds.isEmpty ? nil : st?.dropM,
            "descents": ds.isEmpty ? nil : Double(ds.count),
            "longest_m": ds.compactMap(\.distM).max(),
            "ski_dist_m": ds.isEmpty ? nil : st?.skiDistM,
        ]
    }

    /// A single-run recording counts for these records only.
    static let runRecords: Set = ["max_kmh", "longest_m"]

    /// The history after this day. Never a position.
    public static func update(_ history: History, _ r: Analysis, _ runs: [String: RunInfo], _ M: MomentConfig) -> History {
        var h = history
        h.v = History.version
        let day = r.mode == "day"
        if day && !descents(r).isEmpty { h.days += 1 }
        for (k, t) in done(r, runs, M) {
            if h.runs[k] == nil { h.runs[k] = t }
            if let c = runs[k]?.color, !c.isEmpty { h.colors[c] = min(t, h.colors[c] ?? t) }
        }
        for (k, v) in dayBests(r) {
            guard let v else { continue }
            if !day && !runRecords.contains(k) { continue }
            if h.best[k] == nil || v > h.best[k]! { h.best[k] = v }
        }
        return h
    }

    public static func moments(_ r: Analysis, history: History?, runs: [String: RunInfo], _ M: MomentConfig) -> Moments {
        let history = history ?? History()
        var items: [Moment] = []
        let end = r.end ?? 0
        let ds = descents(r)
        let day = r.mode == "day"
        let known = history.days > 0 || !history.best.isEmpty
        func add(_ key: String, _ t: Int64, _ p: [String: Value]) { items.append(Moment(key: key, t: t, seed: seed(key, t), p: p)) }
        func longest() -> Segment? {
            var b: Segment?
            for s in ds { if b == nil || (s.distM ?? 0) > (b!.distM ?? 0) || ((s.distM ?? 0) == (b!.distM ?? 0) && s.t0 < b!.t0) { b = s } }
            return b
        }
        let st = r.stats
        // the fastest moment of the day ("here you flew")
        if let kmh = st?.maxKmh, let at = st?.maxAt {
            add("fastest", at.t, ["kmh": .num(kmh), "at": .obj(["lat": .num(at.lat), "lon": .num(at.lon)])]
                .merging(whereAt(legAt(r, at.t))) { $1 })
        }
        // the longest descent
        if let s = longest() {
            add("longest_descent", s.t0, ["t1": .num(Double(s.t1)), "dist_m": .opt(s.distM), "drop_m": .opt(s.dropM),
                                          "moving_s": .opt(s.movingS.map(Double.init)), "runs": .names(mainRuns(s))])
        }
        // the first of each colour today, and whether it is the first ever
        let dn = done(r, runs, M)
        for c in M.colorsFirst {
            if let f = dn.first(where: { runs[$0.k]?.color == c }) {
                add("first_color", f.t, ["color": .str(c), "run": .str(f.k), "first_ever": .bool(history.colors[c] == nil)])
            }
        }
        // runs skied for the first time (only against a history: on the first day everything is new)
        if known {
            let new = dn.filter { history.runs[$0.k] == nil }
            if let f = new.first { add("new_runs", f.t, ["runs": .names(new.map(\.k))]) }
        }
        // personal records against the history, and "all the blues"
        if known {
            let bests = dayBests(r)
            let tOf: [String: Int64] = ["max_kmh": st?.maxAt?.t ?? end, "longest_m": longest()?.t0 ?? end]
            for k in M.records {
                guard let v = bests[k] ?? nil else { continue }
                if !day && !runRecords.contains(k) { continue }
                guard let before = history.best[k], v > before else { continue }
                add("record", tOf[k] ?? end, ["what": .str(k), "value": .num(v), "before": .num(before)])
            }
            var allr: [String: Set<String>] = [:]
            for (k, i) in runs where i.named && i.kind == "run" { if let c = i.color, !c.isEmpty { allr[c, default: []].insert(k) } }
            let had = Set(history.runs.keys)
            let now = had.union(dn.map(\.k))
            for c in M.colorsAll {
                let want = allr[c] ?? []
                if !want.isEmpty && want.isSubset(of: now) && !want.isSubset(of: had) {
                    let t = dn.filter { want.contains($0.k) && !had.contains($0.k) }.map(\.t).max()!
                    add("all_color", t, ["color": .str(c), "n": .int(want.count)])
                }
            }
        }
        // the queues
        var qs: [(s: Int, t0: Int64, lift: String?)] = []
        for (i, s) in r.segments.enumerated() where s.type == "stop" && s.kind == "queue" {
            let nxt = r.segments[(i + 1)...].first { $0.type == "lift" }
            qs.append((pyRound(sec(s.t0, s.t1)), s.t0, nxt?.name))
        }
        var q = qs.first
        for x in qs.dropFirst() where x.s > q!.s || (x.s == q!.s && x.t0 < q!.t0) { q = x }
        if let q, Double(q.s) >= M.queueMinS {
            add("queue", q.t0, ["s": .int(q.s), "lift": q.lift.map { .str($0) } ?? .null,
                                "total_s": .int(qs.reduce(0) { $0 + $1.s }), "count": .int(qs.count)])
        }
        // the drop against known heights: the first in the list that gives a ratio in range
        let drop = st?.dropM ?? 0
        if day && drop >= M.dropMinM {
            for ref in M.dropRefs {
                let times = drop / ref.m
                if M.dropTimes[0] <= times && times <= M.dropTimes[1] {
                    add("drop_compare", end, ["ref": .str(ref.id), "times": .num(round(times, 1)), "drop_m": .num(drop)])
                    break
                }
            }
        }
        // "here you fell": a question, never a safety claim
        for f in r.falls {
            add("fall", f.t, ["at": .obj(["lat": .num(f.lat), "lon": .num(f.lon)])].merging(whereAt(legAt(r, f.t))) { $1 })
        }
        // off-piste descents
        for s in ds {
            for o in s.offpiste ?? [] { add("offpiste", o.t0, ["t1": .num(Double(o.t1)), "drop_m": .num(o.dropM), "dist_m": .num(o.distM)]) }
        }
        func prio(_ m: Moment) -> Double { M.priority[m.key] ?? 0 }
        items = items.enumerated().sorted { a, b in
            let x = a.element, y = b.element
            if x.t != y.t { return x.t < y.t }
            if prio(x) != prio(y) { return prio(x) > prio(y) }
            if x.key != y.key { return pyLess(x.key, y.key) }
            return a.offset < b.offset
        }.map(\.element)
        func sub(_ m: Moment) -> Int {
            guard m.key == "record", case .str(let w)? = m.p["what"] else { return 0 }
            return M.records.firstIndex(of: w) ?? 0
        }
        let rank = items.indices.sorted { i, j in
            let x = items[i], y = items[j]
            if prio(x) != prio(y) { return prio(x) > prio(y) }
            if sub(x) != sub(y) { return sub(x) < sub(y) }
            if x.t != y.t { return x.t < y.t }
            if x.key != y.key { return pyLess(x.key, y.key) }
            return i < j
        }
        // the story: the best moments, at most story_per_key of a kind, in the order of the day
        var story: [Int] = [], per: [String: Int] = [:]
        for i in rank {
            let k = items[i].key
            if story.count < M.storyMax && per[k, default: 0] < M.storyPerKey { story.append(i); per[k, default: 0] += 1 }
        }
        story.sort()
        let share = rank.first { !M.shareExclude.contains(items[$0].key) }
        return Moments(moments: M.version, items: items, story: story, share: share, history: update(history, r, runs, M))
    }

    /// The card on the lift: the last descent before the lift the skier is on now, from the final analysis of the
    /// journal so far. Nil if the last segment is not a lift or there was no descent before it.
    public static func liftCard(_ r: Analysis, _ M: MomentConfig) -> Moment? {
        guard let last = r.segments.last, last.type == "lift",
              let s = r.segments.dropLast().last(where: { $0.type == "descent" }) else { return nil }
        let falls = r.falls.filter { s.t0 <= $0.t && $0.t <= s.t1 }.count
        return Moment(key: "last_descent", t: s.t0, seed: seed("last_descent", s.t0),
                      p: ["t1": .num(Double(s.t1)), "runs": .names(mainRuns(s)), "dist_m": .opt(s.distM), "drop_m": .opt(s.dropM),
                          "moving_s": .opt(s.movingS.map(Double.init)), "max_kmh": .opt(s.maxKmh), "falls": .int(falls)])
    }
}
