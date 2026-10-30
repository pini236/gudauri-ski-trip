import Foundation

/// The analysis of a ski day (decision 76; docs/ARCHITECTURE.md m-8): a port of the reference tool, tools/dayrec
/// (analyze.py and net.py), step by step, checked against the same shared cases (tools/fixtures/dayrec-cases.json).
/// The Python is the specification; tools/dayrec/README.md describes the steps.
public enum DayRec {}

extension DayRec {
    /// tools/fixtures/dayrec-params.json as it is (m-8 condition 4): every threshold lives there, none in code, so a
    /// calibration in the season is a change to that file only. `self["lift", "board_m"]` reads a number of a section.
    public struct Params: Sendable {
        public let analysis: Int
        let gapS: Double
        let resetGapS: Double
        let sections: [String: [String: Double]]
        /// The default cable speed by lift kind, m/s.
        let liftSpeeds: [String: Double]

        public init(_ data: Data) throws {
            guard let o = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let a = (o["analysis"] as? NSNumber)?.intValue,
                  let gap = (o["gap_s"] as? NSNumber)?.doubleValue,
                  let reset = (o["reset_gap_s"] as? NSNumber)?.doubleValue else { throw CocoaError(.fileReadCorruptFile) }
            analysis = a; gapS = gap; resetGapS = reset
            var secs: [String: [String: Double]] = [:]
            var speeds: [String: Double] = [:]
            for (k, v) in o {
                guard let sec = v as? [String: Any] else { continue }
                var m: [String: Double] = [:]
                for (kk, vv) in sec {
                    if let n = vv as? NSNumber { m[kk] = n.doubleValue }
                    else if kk == "default_speed_mps", let d = vv as? [String: Any] {
                        for (kind, n) in d { if let n = n as? NSNumber { speeds[kind] = n.doubleValue } }
                    }
                }
                secs[k] = m
            }
            sections = secs; liftSpeeds = speeds
            moments = try (o["moments"] as? [String: Any]).map {
                try JSONDecoder().decode(MomentConfig.self, from: JSONSerialization.data(withJSONObject: $0))
            }
        }

        /// The moments engine's section (nil in a file from before it).
        public let moments: MomentConfig?

        /// A number of a section; a missing one is a broken file, found by the tests, never in the field.
        subscript(_ section: String, _ key: String) -> Double {
            guard let v = sections[section]?[key] else { preconditionFailure("dayrec-params: no \(section).\(key)") }
            return v
        }

        func int(_ section: String, _ key: String) -> Int { Int(self[section, key]) }

        func liftSpeed(_ kind: String?) -> Double {
            guard let v = kind.flatMap({ liftSpeeds[$0] }) ?? liftSpeeds["other"] else { preconditionFailure("dayrec-params: no lift.default_speed_mps.other") }
            return v
        }
    }
}
