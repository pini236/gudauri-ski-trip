import Foundation

/// Where the ski days live on the phone (decision 76; docs/ARCHITECTURE.md m-8, answer 3): one folder, a journal per
/// day (`<start in ms>.jsonl`), never sent anywhere and kept out of the iCloud backup, so the tracks really stay on the
/// phone. The analysis file of a day (`<start>.json`, written by the analyser) sits next to its journal.
public struct DayStore: Sendable {
    /// 10 MB a day at most (m-8): about 28,800 one-second points in eight hours take some 3 MB.
    public static let cap = 10 * 1024 * 1024

    public let dir: URL

    public init(dir: URL) throws {
        self.dir = dir
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        #if canImport(Darwin)
        var u = dir, v = URLResourceValues()
        v.isExcludedFromBackup = true
        try u.setResourceValues(v)
        #endif
    }

    public struct Entry: Equatable, Sendable {
        public let start: Int64
        public let url: URL
        public let bytes: Int
    }

    /// Every day's journal, oldest first.
    public func days() -> [Entry] {
        let fm = FileManager.default
        let files = (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return files.compactMap { u -> Entry? in
            guard u.pathExtension == "jsonl", let s = Int64(u.deletingPathExtension().lastPathComponent) else { return nil }
            let size = (try? u.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            let analysis = dir.appendingPathComponent("\(s).json")
            let more = (try? analysis.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            return Entry(start: s, url: u, bytes: size + more)
        }.sorted { $0.start < $1.start }
    }

    /// The space all the days take, for the history screen.
    public var bytes: Int { days().reduce(0) { $0 + $1.bytes } }

    /// The latest day, if it never got its stop: the app was gone mid-day ("the day was cut, go on?").
    public func interrupted() -> (Entry, DayJournal.Day)? {
        guard let e = days().last, let data = try? Data(contentsOf: e.url), let day = DayJournal.read(data),
              !day.closed else { return nil }
        return (e, day)
    }

    /// A new day's journal, with its header written and synced.
    public func begin(_ header: DayJournal.Header) throws -> DayWriter {
        let url = dir.appendingPathComponent("\(header.start).jsonl")
        guard FileManager.default.createFile(atPath: url.path, contents: nil) else { throw CocoaError(.fileWriteUnknown) }
        let w = try DayWriter(url: url)
        w.append(.header(header))
        try w.sync()
        return w
    }

    /// Goes on writing an interrupted day.
    public func resume(_ e: Entry, at t: Int64) throws -> DayWriter {
        let w = try DayWriter(url: e.url)
        w.append(.event(.init(.resume, t: t, reason: "crash_recovery")))
        try w.sync()
        return w
    }

    public func delete(_ e: Entry) {
        try? FileManager.default.removeItem(at: e.url)
        try? FileManager.default.removeItem(at: dir.appendingPathComponent("\(e.start).json"))
    }

    public func deleteAll() { for e in days() { delete(e) } }
}

/// Appends to one day's journal. Lines wait in memory and reach the disk, synced, at most every 30 seconds (m-8:
/// a crash loses half a minute, not a day), and on every stop. Not thread-safe: one owner writes.
public final class DayWriter {
    public static let syncEvery: Int64 = 30_000

    private let handle: FileHandle
    private var pending = Data()
    private var lastSync: Int64 = 0
    public private(set) var bytes: Int
    /// The cap was reached: the `full` event is written and nothing after it.
    public private(set) var full = false

    init(url: URL) throws {
        handle = try FileHandle(forWritingTo: url)
        bytes = Int(try handle.seekToEnd())
    }

    deinit { try? handle.close() }

    /// Adds a line; syncs when `now` (epoch ms) is 30 seconds past the last sync. False once the day is full.
    @discardableResult
    public func append(_ line: DayJournal.Line, now: Int64? = nil) -> Bool {
        if full { return false }
        let d = DayJournal.encode(line)
        if bytes + pending.count + d.count > DayStore.cap - 256 {
            full = true
            pending.append(DayJournal.encode(.event(.init(.full, t: now ?? 0))))
            try? sync()
            return false
        }
        pending.append(d)
        if let now, now - lastSync >= Self.syncEvery { try? sync(now: now) }
        return true
    }

    /// Everything pending to the disk, and fsync.
    public func sync(now: Int64? = nil) throws {
        if !pending.isEmpty {
            try handle.write(contentsOf: pending)
            bytes += pending.count
            pending.removeAll(keepingCapacity: true)
        }
        try handle.synchronize()
        if let now { lastSync = now }
    }

    /// The stop line, synced; the writer is done.
    public func close(_ t: Int64, reason: String) throws {
        full = false
        pending.append(DayJournal.encode(.event(.init(.stop, t: t, reason: reason))))
        try sync(now: t)
        try handle.close()
        full = true
    }
}
