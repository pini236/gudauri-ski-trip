import Foundation
import GudiDomain

#if DAYREC
/// The skier's history and the moments of each day, on the phone only (decision 76; the architect, 30.10.2026):
/// beside the day's journal, never sent anywhere. Android's `dayrec/MomentsStore.kt`.
///
/// - `<start>.moments.json`: what the engine chose for that day (items, story, share, theme).
/// - `moments-history.json`: the skier's own history (numbers only, never a position).
/// - `moments-applied.json`: the days already counted in it, so a day is counted once even when it is worked out again.
///
/// The example day is read against an empty history and leaves nothing in the real one.
enum MomentsStore {
    static let historyFile = "moments-history.json"
    static let appliedFile = "moments-applied.json"

    static func momentsURL(_ dir: URL, _ start: Int64) -> URL { dir.appendingPathComponent("\(start).moments.json") }

    static func history(_ dir: URL) -> DayRec.History? {
        guard let d = try? Data(contentsOf: dir.appendingPathComponent(historyFile)) else { return nil }
        return try? JSONDecoder().decode(DayRec.History.self, from: d)
    }

    static func applied(_ dir: URL) -> Set<Int64> {
        guard let d = try? Data(contentsOf: dir.appendingPathComponent(appliedFile)),
              let a = try? JSONDecoder().decode([Int64].self, from: d) else { return [] }
        return Set(a)
    }

    static func clear(_ dir: URL) {
        for f in [historyFile, appliedFile] { try? FileManager.default.removeItem(at: dir.appendingPathComponent(f)) }
    }

    static func write(_ url: URL, _ data: Data) { try? data.write(to: url, options: .atomic) }

    /// "Your trip" for the moments: the key of the trip (its first ski day) and whether this day is its last ski day.
    /// Nil when there is no trip, or the day falls outside its ski days (the trip's moments then do not appear).
    static func tripKey(_ trip: Trip?, start: Int64, utcOffsetH: Double) -> DayRec.TripKey? {
        guard let days = trip?.skiDays() else { return nil }
        let day = Day(epochDay: Int(((Double(start) / 1000 + utcOffsetH * 3600) / 86_400).rounded(.down)))
        guard day >= days.lowerBound, day <= days.upperBound else { return nil }
        return DayRec.TripKey(start: days.lowerBound.description, last: day == days.upperBound)
    }
}

extension DayEngine {
    /// The story of a day: what the engine chose, worked out once and kept beside the journal. The example day never
    /// touches the real history.
    func story(_ e: DayStore.Entry, dir: URL, sample: Bool, trip: DayRec.TripKey?) -> DayRec.Moments? {
        guard let a = ready(), let config = a.params.moments, let r = final(e, dir: dir) else { return nil }
        let start = e.start
        let saved = MomentsStore.momentsURL(dir, start)
        let counted = sample ? [] : MomentsStore.applied(dir)
        if counted.contains(start), let d = try? Data(contentsOf: saved),
           let m = try? JSONDecoder().decode(DayRec.Moments.self, from: d), m.moments == config.version { return m }
        let out = DayRec.moments(r, history: sample ? nil : MomentsStore.history(dir), runs: a.net.runs, config, trip: sample ? nil : trip)
        if let d = try? JSONEncoder().encode(out) { MomentsStore.write(saved, d) }
        guard !sample else { return out }
        if let d = try? JSONEncoder().encode(out.history) { MomentsStore.write(dir.appendingPathComponent(MomentsStore.historyFile), d) }
        if let d = try? JSONEncoder().encode(Array(counted.union([start])).sorted()) {
            MomentsStore.write(dir.appendingPathComponent(MomentsStore.appliedFile), d)
        }
        return out
    }
}
#endif
