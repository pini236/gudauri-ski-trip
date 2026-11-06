import Foundation

/// The ski day's screens as pure values (round 29): the live numbers as text, the speed scale of the day's map, and the end
/// of the day as chapters of cards. Android's dayrec/ui/LiveNumbers.kt, SpeedScale.kt and DayModel.kt, with the same tests.
/// Nothing here reads a position (the result has none, but the max speed's place and the falls).
extension DayRec {
    public enum LiveNumbers {
        /// A speed in m/s as whole km/h; "0" while there is none.
        public static func kmh(_ ms: Double?) -> String { guard let ms, ms >= 0 else { return "0" }; return String(Int((ms * 3.6).rounded())) }
        /// A maximum in km/h, rounded.
        public static func maxKmh(_ kmh: Double?) -> String { guard let kmh else { return "0" }; return String(Int(kmh.rounded())) }
        /// Metres as kilometres with one decimal, a dot in every language (the digits of the site).
        public static func km(_ m: Double?) -> String { String(format: "%.1f", (m ?? 0) / 1000) }
        public static func metres(_ m: Double?) -> String { String(Int((m ?? 0).rounded())) }
        /// How long the recording has gone on: "0:12", and "1:05:03" after the first hour.
        public static func elapsed(ms: Int64) -> String {
            let s = max(0, ms / 1000), h = s / 3600, m = s % 3600 / 60, ss = s % 60
            return h > 0 ? String(format: "%d:%02d:%02d", h, m, ss) : String(format: "%d:%02d", m, ss)
        }
        /// No reading for this long while recording: the signal is weak.
        public static let weakMs: Int64 = 20_000
        public static func weakGps(now: Int64, lastFix: Int64, startedAt: Int64) -> Bool {
            let since = lastFix > 0 ? lastFix : startedAt
            return since > 0 && now - since > weakMs
        }
    }

    /// The speed scale of the day's map: its own, so it is not mixed with the run or slope colours. 0 light yellow, 20 orange,
    /// 40 red, 60 and up purple, blended between.
    public enum SpeedScale {
        private static let stops: [(Double, UInt32)] = [(0, 0xF5D76E), (20, 0xF08A24), (40, 0xD62B2B), (60, 0x7B2D9B)]
        /// The colour as 0xRRGGBB for `kmh`; nil or negative is the first.
        public static func rgb(_ kmh: Double?) -> UInt32 {
            let v = min(max(kmh ?? 0, 0), stops.last!.0)
            for i in 0..<(stops.count - 1) {
                let (a, ca) = stops[i], (b, cb) = stops[i + 1]
                if v <= b { return mix(ca, cb, (v - a) / (b - a)) }
            }
            return stops.last!.1
        }
        private static func mix(_ a: UInt32, _ b: UInt32, _ f: Double) -> UInt32 {
            func ch(_ shift: UInt32) -> UInt32 {
                let x = Double((a >> shift) & 0xFF) * (1 - f) + Double((b >> shift) & 0xFF) * f
                return UInt32(min(max(Int(x), 0), 255)) << shift
            }
            return ch(16) | ch(8) | ch(0)
        }
    }

    /// The end of the day, as the screens draw it (the timeline): from an analysis to chapters of cards. The timeline is the
    /// truth (the live screen only guessed): a descent is a card with its runs and top speed; a lift appears only as the queue
    /// before it; a long rest is a bar; a stretch off the runs is said so and never given a name.
    public enum DayModel {
        public enum Item: Equatable, Sendable {
            case descent(Descent)
            /// A wait for a lift of at least `queueMin` minutes; `lift` is the lift after it, when there was one.
            case queue(t0: Int64, minutes: Int, lift: String?)
            case rest(t0: Int64, minutes: Int, long: Bool)
            case walk(t0: Int64, distM: Double)

            public var t0: Int64 {
                switch self {
                case .descent(let d): return d.t0
                case .queue(let t, _, _), .rest(let t, _, _), .walk(let t, _): return t
                }
            }
        }

        public struct Descent: Equatable, Sendable {
            public var t0: Int64
            public var runs: [String]
            public var maxKmh: Double?
            public var offRuns: Bool
            public var dropM: Double, distM: Double
            /// The card of a descent: its end, the time it moved, its average, and when the top speed was.
            public var t1: Int64
            public var movingS: Double
            public var avgKmh: Double?
            public var maxAt: Int64?
        }

        public struct Chapter: Equatable, Sendable {
            public var afternoon: Bool
            public var items: [Item]
            public var descents: Int { items.filter { if case .descent = $0 { return true }; return false }.count }
        }

        public struct Summary: Equatable, Sendable {
            public var start: Int64, end: Int64
            public var descents: Int, lifts: Int
            public var dropM: Double, distM: Double
            public var maxKmh: Double?
            public var maxAt: Int64?, maxLat: Double?, maxLon: Double?
        }

        public struct Day: Equatable, Sendable { public var summary: Summary; public var chapters: [Chapter] }

        /// A queue shorter than this is not worth a line.
        public static let queueMin = 3
        /// A rest of this many minutes or more is shown.
        public static let restMin = 10
        /// From this hour on (the resort's clock) it is the afternoon.
        public static let noonHour = 13
        /// A walk shorter than this is the skier moving about, not a line of the day.
        public static let walkM = 150.0

        public static func build(_ a: Analysis, zone: TimeZone) -> Day {
            var items: [Item] = []
            for (i, s) in a.segments.enumerated() {
                switch s.type {
                case "descent":
                    var runs: [String] = [], off = false
                    for g in s.legs ?? [] {
                        if g.kind == "run", !g.runs.isEmpty { let n = g.runs.joined(separator: " · "); if !runs.contains(n) { runs.append(n) } }
                        if g.kind == "off" { off = true }
                    }
                    if !(s.offpiste ?? []).isEmpty { off = true }
                    items.append(.descent(Descent(t0: s.t0, runs: runs, maxKmh: s.maxKmh, offRuns: off, dropM: s.dropM ?? 0, distM: s.distM ?? 0,
                                                  t1: s.t1, movingS: Double(s.movingS ?? 0), avgKmh: s.avgKmh, maxAt: s.maxAt?.t)))
                case "stop":
                    let min = Int((s.t1 - s.t0) / 60_000)
                    switch s.kind {
                    case "queue": if min >= queueMin { items.append(.queue(t0: s.t0, minutes: min, lift: nextLift(a.segments, from: i))) }
                    case "long": if min >= restMin { items.append(.rest(t0: s.t0, minutes: min, long: true)) }
                    case "rest": if min >= restMin { items.append(.rest(t0: s.t0, minutes: min, long: false)) }
                    default: break
                    }
                case "walk":
                    let d = s.distM ?? 0
                    if d >= walkM { items.append(.walk(t0: s.t0, distM: d)) }
                default: break
                }
            }
            let st = a.stats
            let summary = Summary(start: a.start ?? 0, end: a.end ?? 0, descents: st?.descents ?? 0, lifts: st?.lifts ?? 0,
                                  dropM: st?.dropM ?? 0, distM: st?.skiDistM ?? 0, maxKmh: st?.maxKmh,
                                  maxAt: st?.maxAt?.t, maxLat: st?.maxAt?.lat, maxLon: st?.maxAt?.lon)
            var cal = Calendar(identifier: .gregorian); cal.timeZone = zone
            var chapters: [Chapter] = []
            for it in items {
                let pm = cal.component(.hour, from: Date(timeIntervalSince1970: Double(it.t0) / 1000)) >= noonHour
                if chapters.last?.afternoon != pm { chapters.append(Chapter(afternoon: pm, items: [])) }
                chapters[chapters.count - 1].items.append(it)
            }
            return Day(summary: summary, chapters: chapters)
        }

        private static func nextLift(_ segs: [Segment], from: Int) -> String? {
            for i in (from + 1)..<max(from + 1, min(segs.count, from + 3)) where segs[i].type == "lift" { return segs[i].name }
            return nil
        }
    }
}
