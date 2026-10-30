import Foundation

extension DayRec {
    /// The day as the analysis sees it, in the final mode (tools/dayrec/README.md, "The analysis result"). Encoded with
    /// `DayRec.encoder`, it is the reference tool's JSON (snake_case keys), so the two compare by compare.py's rules.
    public struct Analysis: Codable, Equatable, Sendable {
        /// The algorithm's version (params' `analysis`): a result can always be computed again from the journal.
        public var analysis: Int
        public var v: Int
        public var resort: String?
        /// `day`, or `run` (timing one descent).
        public var mode: String
        public var points: Int
        public var start: Int64?
        public var end: Int64?
        public var baro: Bool?
        /// Readings over seconds, first to last.
        public var coverage: Double?
        /// Holes of more than `gap_s` between two readings.
        public var gaps: [Span]
        /// "Here you fell": probably. Fun, not safety (m-8, conditions 9 and 10).
        public var falls: [Spot]
        public var segments: [Segment]
        public var stats: Stats?
    }

    public struct Span: Codable, Equatable, Sendable {
        public var t0: Int64, t1: Int64
    }

    public struct Spot: Codable, Equatable, Sendable {
        public var t: Int64, lat: Double, lon: Double
    }

    /// A lift's station at one end of a ride; nil when the rider got on or off where no station is mapped.
    public struct StationRef: Codable, Equatable, Sendable {
        public var station: String?
        public var unmapped: Bool?

        public init(station: String?) { self.station = station; unmapped = station == nil ? true : nil }

        public func encode(to e: Encoder) throws {
            var c = e.container(keyedBy: CodingKeys.self)
            try c.encode(station, forKey: .station)   // null, not missing, for an unmapped stop
            try c.encodeIfPresent(unmapped, forKey: .unmapped)
        }
    }

    /// A leg of a descent: `run` (on named runs; two or more on a shared stretch), `unnamed`, `off` or `unmatched`.
    public struct Leg: Codable, Equatable, Sendable {
        public var t0: Int64, t1: Int64
        public var kind: String
        public var runs: [String]
        public var distM: Double
    }

    public struct OffPiste: Codable, Equatable, Sendable {
        public var t0: Int64, t1: Int64
        public var dropM: Double, distM: Double
    }

    /// One piece of the day, in order. `type` is lift, descent, stop, walk, ascent or drive (a car on a road, not
    /// in the skiing figures); the fields are those of its type. A client shows a type it does not know as unknown.
    public struct Segment: Codable, Equatable, Sendable {
        public var type: String
        public var t0: Int64, t1: Int64
        // lift
        public var lift: String?
        public var name: String?
        public var from: StationRef?
        public var to: StationRef?
        /// lift, ascent and drive (negative going down)
        public var riseM: Double?
        // descent
        public var legs: [Leg]?
        /// descent, walk and drive
        public var distM: Double?
        public var dropM: Double?
        public var movingS: Int?
        public var avgKmh: Double?
        public var maxKmh: Double?
        public var maxAt: Spot?
        public var stops: [Span]?
        public var offpiste: [OffPiste]?
        /// Calibrated altitude at the top and bottom, nil before any station calibrated it.
        public var topM: Double?
        public var bottomM: Double?
        // stop
        public var lat: Double?
        public var lon: Double?
        /// queue, rest or long.
        public var kind: String?

        init(type: String, t0: Int64, t1: Int64) { self.type = type; self.t0 = t0; self.t1 = t1 }
    }

    public struct Stats: Codable, Equatable, Sendable {
        public var descents: Int, lifts: Int
        public var skiDistM: Double, dropM: Double
        public var skiS: Int, liftS: Int
        public var liftRiseM: Double
        public var maxKmh: Double?
        public var maxAt: Spot?
    }

    /// The live view (README, "The live view"): every change of what the screen says, each from readings up to then.
    public struct Live: Codable, Equatable, Sendable {
        public struct Change: Codable, Equatable, Sendable {
            public var t: Int64
            /// stand, walk, ski, lift, up or drive.
            public var act: String
            public var lift: String?
            /// While skiing: the run names, empty on a line without a name, nil off the runs.
            public var runs: [String]?
        }
        public struct Fall: Codable, Equatable, Sendable {
            public var t: Int64
            /// When it is known (t + settle_s + still_s).
            public var at: Int64
        }
        public struct Autostop: Codable, Equatable, Sendable {
            public var t: Int64
            /// lift, station or still.
            public var reason: String
        }
        public var events: [Change]
        public var maxKmh: Double?
        public var falls: [Fall]
        /// Timing one descent only: when it stops by itself.
        public var autostop: Autostop?
    }

    /// The reference tool's JSON.
    public static var encoder: JSONEncoder {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        e.outputFormatting = [.sortedKeys]
        return e
    }
}
