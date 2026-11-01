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
        /// Hours from UTC to the resort's clock (the queue pattern splits on a local hour).
        public let utcOffsetH: Double
        /// Kept out of story_max and put at the end of the story, in this order.
        public let storyLast: [String]
        /// A day without a comparison against my own days gets the first ranked of these in its place.
        public let storyFallback: [String]
        public let insights: Insights

        /// The thresholds of the insight moments (against the skier's own days only).
        public struct Insights: Decodable, Sendable, Equatable {
            public let vsPrevRise: Double, vsFirstTimes: Double
            public let flowMinEach: Int, flowMinFewer: Int
            public let favoriteMin: Int, favoriteShare: Double
            public let timeSplitMinDescents: Int
            public let steepMinDeg: Int
            public let queuePatternEach: Int, queuePatternMinMin: Double
            public let exploreMinNew: Int, finishMax: Int, tripMinDays: Int

            enum CodingKeys: String, CodingKey {
                case vsPrevRise = "vs_prev_rise", vsFirstTimes = "vs_first_times", flowMinEach = "flow_min_each"
                case flowMinFewer = "flow_min_fewer", favoriteMin = "favorite_min", favoriteShare = "favorite_share"
                case timeSplitMinDescents = "time_split_min_descents", steepMinDeg = "steep_min_deg"
                case queuePatternEach = "queue_pattern_each", queuePatternMinMin = "queue_pattern_min_min"
                case exploreMinNew = "explore_min_new", finishMax = "finish_max", tripMinDays = "trip_min_days"
            }
        }

        enum CodingKeys: String, CodingKey {
            case version, records, priority, insights
            case utcOffsetH = "utc_offset_h", storyLast = "story_last", storyFallback = "story_fallback"
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

    /// What the history keeps of one day: numbers only, never a place.
    public struct DaySum: Codable, Equatable, Sendable {
        public var dropM: Double, descents: Double, skiDistM: Double
        enum CodingKeys: String, CodingKey { case dropM = "drop_m", descents, skiDistM = "ski_dist_m" }
        func value(_ k: String) -> Double? {
            switch k { case "drop_m": return dropM; case "descents": return descents; case "ski_dist_m": return skiDistM; default: return nil }
        }
    }

    /// The trip so far (numbers and run keys only): restarts when `start` changes.
    public struct TripTotals: Codable, Equatable, Sendable {
        public struct FirstOf: Codable, Equatable, Sendable {
            public var run: String
            public var dayN: Int
            enum CodingKeys: String, CodingKey { case run, dayN = "day_n" }
        }
        public var start: String
        public var days: Int
        public var dropM: Double
        /// Run key: the days on it during the trip.
        public var runs: [String: Int]
        /// Colour: the run and the day of the trip it was first skied ever.
        public var first: [String: FirstOf]
        enum CodingKeys: String, CodingKey { case start, days, dropM = "drop_m", runs, first }
    }

    /// "Your trip", as the app knows it: `start` is a key of the trip (its first ski day), `last` that today is its last
    /// ski day.
    public struct TripKey: Equatable, Sendable {
        public var start: String, last: Bool
        public init(start: String, last: Bool) { self.start = start; self.last = last }
    }

    /// The skier's history on the phone, never with a position: the first time of each run and colour, the bests,
    /// the first and the last day (numbers), and the trip so far. A version 1 history reads as it is.
    public struct History: Codable, Equatable, Sendable {
        public static let version = 2
        public var v = History.version
        public var days = 0
        public var runs: [String: Int64] = [:]
        public var colors: [String: Int64] = [:]
        public var best: [String: Double] = [:]
        public var first: DaySum?
        public var prev: DaySum?
        public var trip: TripTotals?
        public init() {}

        enum CodingKeys: String, CodingKey { case v, days, runs, colors, best, first, prev, trip }

        public init(from d: Decoder) throws {
            let c = try d.container(keyedBy: CodingKeys.self)
            v = try c.decodeIfPresent(Int.self, forKey: .v) ?? 1
            days = try c.decodeIfPresent(Int.self, forKey: .days) ?? 0
            runs = try c.decodeIfPresent([String: Int64].self, forKey: .runs) ?? [:]
            colors = try c.decodeIfPresent([String: Int64].self, forKey: .colors) ?? [:]
            best = try c.decodeIfPresent([String: Double].self, forKey: .best) ?? [:]
            first = try c.decodeIfPresent(DaySum.self, forKey: .first)
            prev = try c.decodeIfPresent(DaySum.self, forKey: .prev)
            trip = try c.decodeIfPresent(TripTotals.self, forKey: .trip)
        }

        public func encode(to e: Encoder) throws {
            var c = e.container(keyedBy: CodingKeys.self)
            try c.encode(v, forKey: .v); try c.encode(days, forKey: .days); try c.encode(runs, forKey: .runs)
            try c.encode(colors, forKey: .colors); try c.encode(best, forKey: .best)
            try c.encode(first, forKey: .first); try c.encode(prev, forKey: .prev); try c.encode(trip, forKey: .trip)   // null, not missing
        }
    }

    public struct Moments: Codable, Equatable, Sendable {
        public var moments: Int
        /// By time.
        public var items: [Moment]
        /// Indexes into items, by time (the end of the trip and the suggestion for tomorrow last).
        public var story: [Int]
        public var share: Int?
        /// The theme of the day, for the title of the story; nil on a day without a descent.
        public var theme: String?
        /// The history after this day.
        public var history: History

        public func encode(to e: Encoder) throws {
            var c = e.container(keyedBy: CodingKeys.self)
            try c.encode(moments, forKey: .moments); try c.encode(items, forKey: .items); try c.encode(story, forKey: .story)
            try c.encode(share, forKey: .share)   // null, not missing
            try c.encode(theme, forKey: .theme)
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
    /// What the history keeps of a day.
    static let daySum = ["drop_m", "descents", "ski_dist_m"]
    static let colorOrder = ["green", "blue", "red", "black"]

    static func deg(_ g: Double) -> Int { pyRound(atan(g) * 180 / .pi) }

    /// The steepest run skied today (a leg of at least color_min_m on it), by its steepest ~100 m.
    static func steepest(_ r: Analysis, _ runs: [String: RunInfo], _ M: MomentConfig) -> (run: String, deg: Int, t: Int64)? {
        var best: (run: String, deg: Int, t: Int64)?
        for (k, t) in done(r, runs, M) {
            let d = deg(runs[k]?.steepG ?? 0)
            if best == nil || d > best!.deg { best = (k, d, t) }
        }
        return best
    }

    /// The colours of a descent: of each run it went on for at least color_min_m (legs on that one run).
    static func descColors(_ s: Segment, _ runs: [String: RunInfo], _ M: MomentConfig) -> Set<String> {
        var d: [String: Double] = [:]
        for l in s.legs ?? [] where l.kind == "run" && l.runs.count == 1 { d[l.runs[0], default: 0] += l.distM }
        var out: Set<String> = []
        for (k, m) in d where m >= M.colorMinM { if let c = runs[k]?.color, !c.isEmpty { out.insert(c) } }
        return out
    }

    /// The queues: a queue stop (seconds, start, the lift after it).
    static func queues(_ r: Analysis) -> [(s: Int, t0: Int64, lift: String?)] {
        var qs: [(s: Int, t0: Int64, lift: String?)] = []
        for (i, s) in r.segments.enumerated() where s.type == "stop" && s.kind == "queue" {
            let nxt = r.segments[(i + 1)...].first { $0.type == "lift" }
            qs.append((pyRound(sec(s.t0, s.t1)), s.t0, nxt?.name))
        }
        return qs
    }

    /// A lift whose queues were shorter on one side of an hour than on the other (the largest difference).
    static func queuePattern(_ r: Analysis, _ M: MomentConfig) -> (lift: String, split: Int, early: Int, late: Int)? {
        let I = M.insights
        var by: [String: [(t0: Int64, s: Int)]] = [:]
        for q in queues(r) { if let l = q.lift, !l.isEmpty { by[l, default: []].append((q.t0, q.s)) } }
        var best: (lift: String, split: Int, early: Int, late: Int)?
        for lift in by.keys.sorted(by: pyLess) {
            let q = by[lift]!.sorted { $0.t0 != $1.t0 ? $0.t0 < $1.t0 : $0.s < $1.s }
            func hour(_ t: Int64) -> Double { (Double(t / 1000) / 3600.0 + M.utcOffsetH).truncatingRemainder(dividingBy: 24) }
            let hours = Set(q.map { Int(hour($0.t0)) + 1 }).sorted()
            for hsplit in hours {
                let a = q.filter { hour($0.t0) < Double(hsplit) }.map(\.s)
                let b = q.filter { !(hour($0.t0) < Double(hsplit)) }.map(\.s)
                if a.count < I.queuePatternEach || b.count < I.queuePatternEach { continue }
                let e = pyRound(Double(a.reduce(0, +)) / Double(a.count) / 60.0)
                let l = pyRound(Double(b.reduce(0, +)) / Double(b.count) / 60.0)
                if Double(abs(e - l)) >= I.queuePatternMinMin && (best == nil || abs(e - l) > abs(best!.early - best!.late)) {
                    best = (lift, hsplit, e, l)
                }
            }
        }
        return best
    }

    /// The trip so far after this day (a copy): ski days, drop, the days on each run, and the colours skied for the first
    /// time ever during the trip.
    static func tripAdd(_ tr: TripTotals, _ history: History, _ r: Analysis, _ runs: [String: RunInfo], _ M: MomentConfig) -> TripTotals {
        var t = tr
        t.days = tr.days + 1
        t.dropM = round(tr.dropM + (r.stats?.dropM ?? 0), 1)
        for (k, _) in done(r, runs, M) {
            t.runs[k, default: 0] += 1
            if let c = runs[k]?.color, !c.isEmpty, history.colors[c] == nil, t.first[c] == nil {
                t.first[c] = TripTotals.FirstOf(run: k, dayN: t.days)
            }
        }
        return t
    }

    /// The history after this day: first time of each run and colour, the personal bests, the first and the last day, and
    /// (when the app passes "your trip") the trip so far. Never a position.
    public static func update(_ history: History, _ r: Analysis, _ runs: [String: RunInfo], _ M: MomentConfig,
                              trip: TripKey? = nil) -> History {
        var h = history
        h.v = History.version
        let day = r.mode == "day"
        let hasDescents = !descents(r).isEmpty
        if day && hasDescents, let trip {
            var tr = history.trip
            if tr == nil || tr!.start != trip.start { tr = TripTotals(start: trip.start, days: 0, dropM: 0, runs: [:], first: [:]) }
            h.trip = tripAdd(tr!, history, r, runs, M)
        }
        if day && hasDescents {
            h.days += 1
            let b = dayBests(r)
            let sum = DaySum(dropM: (b["drop_m"] ?? nil) ?? 0, descents: (b["descents"] ?? nil) ?? 0, skiDistM: (b["ski_dist_m"] ?? nil) ?? 0)
            h.prev = sum
            if h.first == nil { h.first = sum }
        }
        if let st = steepest(r, runs, M), Double(st.deg) > (h.best["steep_deg"] ?? -1) { h.best["steep_deg"] = Double(st.deg) }
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

    public static func moments(_ r: Analysis, history: History?, runs: [String: RunInfo], _ M: MomentConfig,
                               trip: TripKey? = nil) -> Moments {
        let history = history ?? History()
        let newH = update(history, r, runs, M, trip: trip)
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
            var p: [String: Value] = ["kmh": .num(kmh), "at": .obj(["lat": .num(at.lat), "lon": .num(at.lon)])]
            p.merge(whereAt(legAt(r, at.t))) { $1 }
            // the how-manieth descent of the day, of how many, and (with two or more) in which half
            if let i = ds.firstIndex(where: { $0.t0 <= at.t && at.t <= $0.t1 }) {
                let nth = i + 1
                p["nth"] = .int(nth); p["of"] = .int(ds.count)
                if ds.count >= 2 { p["when"] = .str(2 * nth <= ds.count ? "early" : "late") }
            }
            add("fastest", at.t, p)
        }
        // the longest descent
        if let s = longest() {
            add("longest_descent", s.t0, ["t1": .num(Double(s.t1)), "dist_m": .opt(s.distM), "drop_m": .opt(s.dropM),
                                          "moving_s": .opt(s.movingS.map(Double.init)), "runs": .names(mainRuns(s)),
                                          "stops": .int((s.stops ?? []).count)])
        }
        // the first of each colour today, and whether it is the first ever
        let dn = done(r, runs, M)
        for c in M.colorsFirst {
            if let f = dn.first(where: { runs[$0.k]?.color == c }) {
                add("first_color", f.t, ["color": .str(c), "run": .str(f.k), "first_ever": .bool(history.colors[c] == nil),
                                         "day_n": .int(history.days + 1),
                                         "n": .int(ds.filter { descColors($0, runs, M).contains(c) }.count)])
            }
        }
        // runs skied for the first time (only against a history: on the first day everything is new)
        if known {
            let new = dn.filter { history.runs[$0.k] == nil }
            if let f = new.first {
                let ever = Set(history.runs.keys).union(dn.map(\.k))
                let total = runs.values.filter { $0.named && $0.kind == "run" && !($0.color ?? "").isEmpty }.count
                add("new_runs", f.t, ["runs": .names(new.map(\.k)), "done": .int(ever.count), "total": .int(total)])
            }
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
        let qs = queues(r)
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
        // insights: against my own days only, never against other people, never praising speed
        let I = M.insights
        let bests = dayBests(r)
        // against the first day or the day before: only a rise
        if day && !ds.isEmpty {
            var cands: [(vs: String, k: String, ratio: Double, before: Double, idx: Int)] = []
            let refs: [(String, DaySum?, Double)] = [("first", history.first, I.vsFirstTimes), ("prev", history.prev, 1 + I.vsPrevRise)]
            for (vs, ref, need) in refs {
                guard let ref, !(vs == "first" && history.days < 2) else { continue }
                for (i, k) in daySum.enumerated() {
                    if let b = bests[k] ?? nil, b != 0, let rv = ref.value(k), rv != 0, b / rv >= need { cands.append((vs, k, b / rv, rv, i)) }
                }
                if !cands.isEmpty { break }   // the first day, when it says something, says more than the day before
            }
            var pick: (vs: String, k: String, ratio: Double, before: Double, idx: Int)?
            for c in cands where pick == nil || c.ratio > pick!.ratio || (c.ratio == pick!.ratio && c.idx < pick!.idx) { pick = c }
            if let c = pick, let b = bests[c.k] ?? nil {
                add("vs_day", end, ["vs": .str(c.vs), "what": .str(c.k), "value": .num(b), "before": .num(c.before), "times": .num(round(c.ratio, 1))])
            }
        }
        // flow: fewer stops inside descents in the second half of the day
        if ds.count >= 2 * I.flowMinEach {
            let h = ds.count / 2
            let am = Array(ds[..<h]), pm = Array(ds[h...])
            let a = am.reduce(0) { $0 + ($1.stops ?? []).count }, b = pm.reduce(0) { $0 + ($1.stops ?? []).count }
            if a - b >= I.flowMinFewer {
                add("flow", pm[0].t0, ["am": .int(a), "am_n": .int(am.count), "pm": .int(b), "pm_n": .int(pm.count)])
            }
        }
        // the favourite run of the day
        if !ds.isEmpty {
            var cnt: [String: Int] = [:]
            for x in ds { if let m = mainRuns(x, 1).first { cnt[m, default: 0] += 1 } }
            if let fav = cnt.min(by: { $0.value != $1.value ? $0.value > $1.value : pyLess($0.key, $1.key) }) {
                if fav.value >= I.favoriteMin && Double(fav.value) >= I.favoriteShare * Double(ds.count),
                   let x = ds.first(where: { mainRuns($0, 1).first == fav.key }) {
                    add("favorite", x.t0, ["run": .str(fav.key), "n": .int(fav.value), "of": .int(ds.count)])
                }
            }
        }
        // where the day went: skiing, on lifts, in queues (minutes)
        if day && ds.count >= I.timeSplitMinDescents {
            add("time_split", end, ["ski": .int(pyRound(Double(st?.skiS ?? 0) / 60.0)), "lift_min": .int(pyRound(Double(st?.liftS ?? 0) / 60.0)),
                                    "queue": .int(pyRound(Double(qs.reduce(0) { $0 + $1.s }) / 60.0))])
        }
        // the steepest stretch skied today, and whether it is the steepest ever
        if let sp = steepest(r, runs, M), sp.deg >= I.steepMinDeg {
            let prev = history.best["steep_deg"]
            add("steepest", sp.t, ["run": .str(sp.run), "deg": .int(sp.deg), "ever": .bool(known && prev != nil && Double(sp.deg) > prev!)])
        }
        // a lift whose queue is shorter at one time of the day
        let qp = day ? queuePattern(r, M) : nil
        if let qp { add("queue_pattern", end, ["lift": .str(qp.lift), "split": .int(qp.split), "early": .int(qp.early), "late": .int(qp.late)]) }
        // one suggestion for tomorrow: never a colour harder than the hardest skied today
        if day, let tm = tomorrow(r, history, runs, M, dn, qp) { add("tomorrow", end, tm) }
        // the end of the trip, on its last ski day (from its second day)
        if day && !ds.isEmpty, let trip, trip.last, let tr = newH.trip, tr.start == trip.start, tr.days >= I.tripMinDays {
            var p: [String: Value] = ["days": .int(tr.days), "drop": .int(pyRound(tr.dropM)), "done": .int(tr.runs.count),
                                      "first": .obj(Dictionary(uniqueKeysWithValues: tr.first.map {
                                          ($0.key, Value.obj(["run": .str($0.value.run), "day_n": .int($0.value.dayN)])) }))]
            if let m = tr.runs.min(by: { $0.value != $1.value ? $0.value > $1.value : pyLess($0.key, $1.key) }), m.value >= 2 {
                p["run"] = .str(m.key); p["n"] = .int(m.value)
            }
            add("trip", end, p)
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
            if M.storyLast.contains(k) { continue }
            if story.count < M.storyMax && per[k, default: 0] < M.storyPerKey { story.append(i); per[k, default: 0] += 1 }
        }
        // a day without a comparison against my own days gets an insight in its place
        let keys = Set(story.map { items[$0].key })
        if !keys.contains("vs_day") && keys.isDisjoint(with: M.storyFallback) {
            if let fb = rank.first(where: { M.storyFallback.contains(items[$0].key) }) {
                if story.count >= M.storyMax { story.removeLast() }   // the last ranked of the story
                story.append(fb)
            }
        }
        story.sort()
        for k in M.storyLast { story += items.indices.filter { items[$0].key == k } }   // outside story_max, at the end
        let share = rank.first { !M.shareExclude.contains(items[$0].key) }
        return Moments(moments: M.version, items: items, story: story, share: share, theme: theme(items, ds, M), history: newH)
    }

    /// The theme of the day, for the title of the story: progress (the first of a colour ever, or the longest descent a
    /// record), explore (new runs), endurance (a record of descents, drop or distance), favorite, easy (up to two
    /// descents), else steady. A speed record never sets the theme.
    static func theme(_ items: [Moment], _ ds: [Segment], _ M: MomentConfig) -> String? {
        if ds.isEmpty { return nil }
        func what(_ m: Moment) -> String? { if case .str(let w)? = m.p["what"] { return w }; return nil }
        if items.contains(where: { m in
            if m.key == "first_color", case .bool(true)? = m.p["first_ever"] { return true }
            return m.key == "record" && what(m) == "longest_m"
        }) { return "progress" }
        if items.contains(where: { m in
            if m.key == "new_runs", case .list(let l)? = m.p["runs"] { return l.count >= M.insights.exploreMinNew }
            return false
        }) { return "explore" }
        if items.contains(where: { $0.key == "record" && daySum.contains(what($0) ?? "") }) { return "endurance" }
        if items.contains(where: { $0.key == "favorite" }) { return "favorite" }
        if ds.count <= 2 { return "easy" }
        return "steady"
    }

    /// One suggestion: new_run (a run never skied, of the hardest colour skied today, from the lift ridden most today),
    /// finish_color (up to finish_max runs left of a colour skied today), or early_lift (the queue pattern).
    static func tomorrow(_ r: Analysis, _ history: History, _ runs: [String: RunInfo], _ M: MomentConfig,
                         _ dn: [(k: String, t: Int64)], _ qp: (lift: String, split: Int, early: Int, late: Int)?) -> [String: Value]? {
        let today = Set(dn.compactMap { runs[$0.k]?.color }.filter { colorOrder.contains($0) })
        let ever = Set(history.runs.keys).union(dn.map(\.k))
        func named(_ c: String) -> [String] {
            runs.filter { $0.value.named && $0.value.kind == "run" && $0.value.color == c }.map(\.key)
        }
        if !today.isEmpty {
            let hard = today.max { colorOrder.firstIndex(of: $0)! < colorOrder.firstIndex(of: $1)! }!
            var rides: [String: (n: Int, t0: Int64)] = [:]
            for s in r.segments where s.type == "lift" {
                if let n = s.name, !n.isEmpty { rides[n] = (((rides[n]?.n) ?? 0) + 1, rides[n]?.t0 ?? s.t0) }
            }
            if let lift = rides.min(by: { $0.value.n != $1.value.n ? $0.value.n > $1.value.n : $0.value.t0 < $1.value.t0 })?.key {
                let cand = named(hard).filter { !ever.contains($0) && (runs[$0]?.fromLifts.contains(lift) ?? false) }.sorted(by: pyLess)
                if let c = cand.first { return ["kind": .str("new_run"), "run": .str(c), "color": .str(hard), "lift": .str(lift)] }
            }
            for c in today.sorted(by: { colorOrder.firstIndex(of: $0)! > colorOrder.firstIndex(of: $1)! }) {
                let left = named(c).filter { !ever.contains($0) }.sorted(by: pyLess)
                if left.count >= 1 && left.count <= M.insights.finishMax {
                    return ["kind": .str("finish_color"), "color": .str(c), "n": .int(left.count), "runs": .names(left)]
                }
            }
        }
        if let qp, qp.early < qp.late { return ["kind": .str("early_lift"), "lift": .str(qp.lift), "split": .int(qp.split)] }
        return nil
    }

    /// The card on the lift: the last descent before the lift the skier is on now, from the final analysis of the
    /// journal so far. Nil if the last segment is not a lift or there was no descent before it.
    public static func liftCard(_ r: Analysis, _ M: MomentConfig) -> Moment? {
        guard let last = r.segments.last, last.type == "lift",
              let s = r.segments.dropLast().last(where: { $0.type == "descent" }) else { return nil }
        let falls = r.falls.filter { s.t0 <= $0.t && $0.t <= s.t1 }.count
        let all = descents(r)
        let stops = (s.stops ?? []).count
        return Moment(key: "last_descent", t: s.t0, seed: seed("last_descent", s.t0),
                      p: ["t1": .num(Double(s.t1)), "runs": .names(mainRuns(s)), "dist_m": .opt(s.distM), "drop_m": .opt(s.dropM),
                          "moving_s": .opt(s.movingS.map(Double.init)), "max_kmh": .opt(s.maxKmh), "falls": .int(falls),
                          "stops": .int(stops), "nonstop": .bool(stops == 0),
                          "longest_so_far": .bool(all.count > 1 && all.allSatisfy { $0 == s || (s.distM ?? 0) > ($0.distM ?? 0) })])
    }
}
