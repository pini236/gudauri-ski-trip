import Foundation

/// The ski day's raw journal (decision 76; docs/ARCHITECTURE.md m-8, answer 3), in the field names of the reference
/// tool (tools/dayrec/README.md, "the raw journal"), which writes the shared cases in it: one file of JSON lines per day, in the
/// app's private folder. The first line is the header, then one line per reading (about one a second), and event lines
/// (`ev`) for a stop, a resume or a full file. Appended to only; a crash loses at most the lines since the last sync,
/// and a cut last line is dropped when read. Readers ignore fields they do not know, so `v` grows only on a break.
///
/// The recorder has no logic beyond writing safely: what the day was (lifts, runs, speeds) is the analyser's, which can
/// always run again over this file.
public enum DayJournal {
    public static let version = 1

    public struct Header: Codable, Equatable, Sendable {
        public var v: Int
        public var tz: String
        public var resort: String
        /// The phone's model, for calibration only (never a name or an id).
        public var device: String
        /// When the day began, epoch ms UTC.
        public var start: Int64
        /// `day`, or `run` for timing one descent (section י 1a), which stops by itself (stop reason `auto`).
        public var mode: String?

        public init(tz: String, resort: String, device: String, start: Int64, mode: String = "day") {
            v = DayJournal.version; self.tz = tz; self.resort = resort; self.device = device; self.start = start; self.mode = mode
        }
    }

    /// One reading. Anything the phone did not give is nil, never zero. The file's keys are the reference tool's short ones.
    public struct Point: Codable, Equatable, Sendable {
        /// Epoch ms UTC.
        public var t: Int64
        public var lat: Double
        public var lon: Double
        /// GPS altitude, metres.
        public var alt: Double?
        /// Barometer: air pressure, hPa (nil without the barometer, the default on iPhone; m-8 addition b).
        public var p: Double?
        /// Doppler speed and its accuracy, m/s.
        public var spd: Double?
        public var sacc: Double?
        /// Horizontal and vertical accuracy, metres.
        public var hacc: Double?
        public var vacc: Double?
        /// Course over ground, degrees.
        public var crs: Double?
        /// The variance of the acceleration's magnitude over the last second, (m/s²)², not the acceleration itself.
        public var mv: Double?

        enum CodingKeys: String, CodingKey {
            case t, lat = "la", lon = "lo", alt = "a", p, spd = "s", sacc = "sa", hacc = "ha", vacc = "va", crs = "b", mv = "m"
        }

        public init(t: Int64, lat: Double, lon: Double, alt: Double? = nil, p: Double? = nil, spd: Double? = nil,
                    sacc: Double? = nil, hacc: Double?, vacc: Double? = nil, crs: Double? = nil, mv: Double? = nil) {
            self.t = t; self.lat = lat; self.lon = lon; self.alt = alt; self.p = p; self.spd = spd; self.sacc = sacc
            self.hacc = hacc; self.vacc = vacc; self.crs = crs; self.mv = mv
        }
    }

    public struct Event: Codable, Equatable, Sendable {
        public enum Kind: String, Codable, Sendable {
            /// Right after the header.
            case start
            /// The day ended; `reason` as in the measurement contract: user, idle, outside, crash_recovery, or auto (a timed run).
            case stop
            /// Recording went on after the app was gone (crash_recovery), or after a stop the user took back.
            case resume
            /// The day reached the 10 MB cap; nothing after it is written (the reference tool skips kinds it does not know).
            case full
            /// A blow ("here you fell", m-8 condition 10): the motion sensors around it, or, with `dismissed`, the
            /// user's "I did not fall" for the blow of the same `t` (the journal is append-only, so it is a second line).
            case impact
        }
        public var ev: Kind
        public var t: Int64
        public var reason: String?
        /// An impact's window: samples a second, the first sample's time, and x, y, z triples of the accelerometer
        /// (m/s², with gravity) and the gyroscope (rad/s).
        public var hz: Double?
        public var t0: Int64?
        public var acc: [Double]?
        public var gyr: [Double]?
        public var dismissed: Bool?

        public init(_ ev: Kind, t: Int64, reason: String? = nil) { self.ev = ev; self.t = t; self.reason = reason }
    }

    public enum Line: Equatable, Sendable {
        case header(Header), point(Point), event(Event)
    }

    /// A whole journal as read back.
    public struct Day: Equatable, Sendable {
        public var header: Header
        public var points: [Point]
        public var events: [Event]
        /// The last line was cut (the app died mid-write) and was dropped.
        public var cut: Bool
        /// Lines dropped anywhere, as the reference reader counts them (tools/dayrec/rawlog.py): not JSON, or neither
        /// an event nor a point with a position.
        public var dropped = 0

        /// Ended by a stop with nothing after it: otherwise the day was interrupted, and can go on.
        public var closed: Bool {
            guard let last = events.last, last.ev == .stop else { return false }
            return (points.last?.t ?? 0) <= last.t
        }
    }

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder(); e.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]; return e
    }()

    /// One line, with its newline.
    public static func encode(_ line: Line) -> Data {
        var d: Data
        switch line {
        case .header(let h): d = try! encoder.encode(h)
        case .point(let p): d = try! encoder.encode(p)
        case .event(let e): d = try! encoder.encode(e)
        }
        d.append(0x0A)
        return d
    }

    /// Reads a journal back. Nil when the first line is not a header this version can read.
    public static func read(_ data: Data) -> Day? {
        let lines = data.split(separator: 0x0A, omittingEmptySubsequences: true)
        guard let first = lines.first, let header = try? JSONDecoder().decode(Header.self, from: Data(first)),
              header.v <= version else { return nil }
        var day = Day(header: header, points: [], events: [], cut: false)
        let d = JSONDecoder()
        for (i, raw) in lines.dropFirst().enumerated() {
            let line = Data(raw)
            if let o = try? JSONSerialization.jsonObject(with: line) as? [String: Any] {
                if o["ev"] != nil {
                    // a kind this version does not know is skipped, not dropped
                    if let e = try? d.decode(Event.self, from: line) { day.events.append(e) }
                    continue
                }
                if let p = try? d.decode(Point.self, from: line) { day.points.append(p); continue }
            }
            day.dropped += 1
            // only the last line can be cut short; a bad line elsewhere (a future kind) is skipped
            if i == lines.count - 2, !data.isEmpty, data.last != 0x0A { day.cut = true }
        }
        // sorted by time, and a second that comes twice keeps its first reading (the reference tool's rule 4)
        var seen = Set<Int64>()
        day.points = day.points.enumerated().sorted { ($0.element.t, $0.offset) < ($1.element.t, $1.offset) }
            .map(\.element).filter { seen.insert($0.t).inserted }
        return day
    }
}
