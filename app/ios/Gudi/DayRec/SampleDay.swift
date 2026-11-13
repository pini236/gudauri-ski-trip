import Foundation
import GudiDomain

#if DAYREC
/// The example day of "My days" (round 29, "דוגמה"): a whole day built from the resort's real lines and lifts (the
/// reference tool's log, `tools/fixtures/dayrec-logs/full_day.jsonl`), so that a new skier, and the App Store reviewer,
/// who is not on the mountain, see a full timeline without snow (`docs/APPLE.md`, 5.9). Android's `SampleDay`.
/// It is marked "דוגמה", opens like any other day, and is never counted: its moments are worked out against an empty
/// history and never written into the real one.
enum SampleDay {
    /// The folder the example lives in: the caches, never the days' folder, so it is never taken for a real day.
    static var folder: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return base.appendingPathComponent("sample-day", isDirectory: true)
    }

    /// The example's journal, unpacked on first use into the caches (it can always be unpacked again).
    /// Never a crash for an example: without the file the day screen says there is nothing in it.
    static func entry() -> DayStore.Entry? {
        let dir = folder
        if let e = (try? DayStore(dir: dir))?.days().first, e.bytes > 0 { return e }
        guard let url = Bundle.main.url(forResource: "sample-day", withExtension: "bin", subdirectory: "DayRec")
                ?? Bundle.main.url(forResource: "sample-day", withExtension: "bin"),
              let packed = try? Data(contentsOf: url),
              // raw DEFLATE, as tools/build-ios-data.py writes it
              let raw = try? (packed as NSData).decompressed(using: .zlib) as Data,
              let start = DayJournal.read(raw)?.header.start else { return nil }
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("\(start).jsonl")
        try? raw.write(to: file, options: .atomic)
        return (try? DayStore(dir: dir))?.days().first
    }
}
#endif
