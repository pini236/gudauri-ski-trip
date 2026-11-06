import XCTest
@testable import GudiDomain

/// The ski day's journal and its folder (decision 76; docs/ARCHITECTURE.md m-8, answer 3 and condition 3): written
/// to the end only, a cut last line dropped, unknown fields ignored, an interrupted day found again.
final class DayJournalTests: XCTestCase {
    private var dir: URL!

    override func setUpWithError() throws {
        dir = FileManager.default.temporaryDirectory.appendingPathComponent("days-\(UUID().uuidString)")
    }

    override func tearDownWithError() throws { try? FileManager.default.removeItem(at: dir) }

    private let header = DayJournal.Header(tz: "Asia/Tbilisi", resort: "gudauri", device: "iPhone15,2", start: 1_768_120_000_000)

    private func point(_ i: Int64) -> DayJournal.Point {
        .init(t: header.start + i * 1000, lat: 42.47 + Double(i) * 1e-5, lon: 44.48, alt: 2100, spd: 4.2, sacc: 0.4, hacc: 5, vacc: 3, crs: 180, mv: 0.01)
    }

    func testRoundTrip() throws {
        var data = DayJournal.encode(.header(header))
        for i in 0..<3 { data.append(DayJournal.encode(.point(point(Int64(i))))) }
        data.append(DayJournal.encode(.event(.init(.stop, t: header.start + 5000, reason: "user"))))
        let day = try XCTUnwrap(DayJournal.read(data))
        XCTAssertEqual(day.header, header)
        XCTAssertEqual(day.points, (0..<3).map { point(Int64($0)) })
        XCTAssertTrue(day.closed)
        XCTAssertFalse(day.cut)
    }

    func testMissingValuesStayNil() throws {
        let p = DayJournal.Point(t: 1, lat: 42.5, lon: 44.5, hacc: 8)
        let line = String(decoding: DayJournal.encode(.point(p)), as: UTF8.self)
        XCTAssertFalse(line.contains(#""s""#), "nothing the phone did not give is written, not even as zero")
        XCTAssertTrue(line.contains(#""la":42.5"#) && line.contains(#""ha":8"#), "the reference tool's short keys")
        var data = DayJournal.encode(.header(header)); data.append(DayJournal.encode(.point(p)))
        XCTAssertNil(DayJournal.read(data)?.points.first?.spd)
    }

    func testCutLastLineIsDropped() throws {
        var data = DayJournal.encode(.header(header))
        data.append(DayJournal.encode(.point(point(0))))
        data.append(DayJournal.encode(.point(point(1))).prefix(30))
        let day = try XCTUnwrap(DayJournal.read(data))
        XCTAssertEqual(day.points.count, 1)
        XCTAssertTrue(day.cut)
        XCTAssertFalse(day.closed)
    }

    func testUnknownFieldsAndLinesAreIgnored() throws {
        var data = Data(#"{"v":1,"tz":"Asia/Tbilisi","resort":"gudauri","device":"x","start":5,"new":true}"#.utf8 + [0x0A])
        data.append(Data(#"{"t":6,"la":42.5,"lo":44.5,"ha":4,"later":[1,2]}"#.utf8 + [0x0A]))
        data.append(Data(#"{"t":6,"la":42.6,"lo":44.6,"ha":4}"#.utf8 + [0x0A]))          // the same second: the first stays
        data.append(Data(#"not json"#.utf8 + [0x0A]))                                    // anywhere in the file
        data.append(Data(#"{"t":7,"a":2000}"#.utf8 + [0x0A]))                            // no la, lo: dropped
        data.append(Data(#"{"ev":"something-new","t":7}"#.utf8 + [0x0A]))
        let day = try XCTUnwrap(DayJournal.read(data))
        XCTAssertEqual(day.points.map(\.t), [6])
        XCTAssertEqual(day.points.first?.lat, 42.5)
        XCTAssertFalse(day.cut)
    }

    func testNewerVersionIsNotRead() {
        let data = Data(#"{"v":2,"tz":"Asia/Tbilisi","resort":"gudauri","device":"x","start":5}"#.utf8 + [0x0A])
        XCTAssertNil(DayJournal.read(data))
    }

    func testWriterSyncsEvery30SecondsAndOnClose() throws {
        let store = try DayStore(dir: dir)
        let w = try store.begin(header)
        let url = store.days()[0].url
        let afterHeader = try Data(contentsOf: url).count
        w.append(.point(point(0)), now: header.start)            // first one syncs
        w.append(.point(point(1)), now: header.start + 10_000)   // waits
        XCTAssertEqual(DayJournal.read(try Data(contentsOf: url))?.points.count, 1)
        w.append(.point(point(2)), now: header.start + 31_000)   // 30 s since the last sync
        XCTAssertEqual(DayJournal.read(try Data(contentsOf: url))?.points.count, 3)
        XCTAssertGreaterThan(try Data(contentsOf: url).count, afterHeader)
        w.append(.point(point(3)), now: header.start + 32_000)
        try w.close(header.start + 33_000, reason: "user")
        let day = try XCTUnwrap(DayJournal.read(try Data(contentsOf: url)))
        XCTAssertEqual(day.points.count, 4)
        XCTAssertTrue(day.closed)
        XCTAssertNil(store.interrupted())
        XCTAssertFalse(w.append(.point(point(4))), "nothing after the stop")
    }

    func testInterruptedDayIsFoundAndResumed() throws {
        let store = try DayStore(dir: dir)
        var w: DayWriter? = try store.begin(header)
        w?.append(.point(point(0)), now: header.start)
        w = nil   // the app is gone, no stop line
        let (entry, day) = try XCTUnwrap(store.interrupted())
        XCTAssertEqual(day.points.count, 1)
        let again = try store.resume(entry, at: header.start + 600_000)
        again.append(.point(point(600)), now: header.start + 600_000)
        try again.close(header.start + 601_000, reason: "user")
        let back = try XCTUnwrap(DayJournal.read(try Data(contentsOf: entry.url)))
        XCTAssertEqual(back.points.count, 2)
        XCTAssertEqual(back.events.map(\.ev), [.start, .resume, .stop])
        XCTAssertEqual(back.events.first { $0.ev == .resume }?.reason, "crash_recovery")
        XCTAssertNil(store.interrupted())
    }

    func testResumeAfterACutLineStartsOnAFreshLine() throws {
        let store = try DayStore(dir: dir)
        var w: DayWriter? = try store.begin(header)
        w?.append(.point(point(0)), now: header.start)
        w = nil
        let url = store.days()[0].url
        let h = try FileHandle(forWritingTo: url); try h.seekToEnd(); try h.write(contentsOf: Data(#"{"t":17681"#.utf8)); try h.close()
        let (entry, _) = try XCTUnwrap(store.interrupted())
        try store.resume(entry, at: header.start + 9000).close(header.start + 9001, reason: "user")
        let back = try XCTUnwrap(DayJournal.read(try Data(contentsOf: url)))
        XCTAssertEqual(back.events.map(\.ev), [.start, .resume, .stop], "the resume line is not glued to the cut one")
        XCTAssertEqual(back.points.count, 1)
    }

    /// Every journal the reference tool wrote for the shared cases reads here (tools/fixtures/dayrec-logs).
    func testTheReferenceLogsRead() throws {
        let logs = Repo.root.appendingPathComponent("tools/fixtures/dayrec-logs")
        guard let names = try? FileManager.default.contentsOfDirectory(atPath: logs.path).filter({ $0.hasSuffix(".jsonl") }),
              !names.isEmpty else { throw XCTSkip("the reference logs are not on this branch yet") }
        for n in names {
            let day = try XCTUnwrap(DayJournal.read(try Data(contentsOf: logs.appendingPathComponent(n))), n)
            XCTAssertFalse(day.points.isEmpty, n)
        }
    }

    func testTheCapStopsWriting() throws {
        let store = try DayStore(dir: dir)
        let w = try store.begin(header)
        var i: Int64 = 0
        while w.append(.point(point(i))) { i += 1 }
        try w.sync()
        let url = store.days()[0].url
        XCTAssertLessThanOrEqual(try Data(contentsOf: url).count, DayStore.cap)
        let day = try XCTUnwrap(DayJournal.read(try Data(contentsOf: url)))
        XCTAssertEqual(day.events.last?.ev, .full)
        XCTAssertEqual(day.points.count, Int(i))
    }

    func testDeleteRemovesTheJournalAndItsAnalysis() throws {
        let store = try DayStore(dir: dir)
        try store.begin(header).close(header.start + 1, reason: "user")
        try Data("{}".utf8).write(to: dir.appendingPathComponent("\(header.start).json"))
        XCTAssertEqual(store.days().count, 1)
        XCTAssertGreaterThan(store.bytes, 2)
        store.deleteAll()
        XCTAssertEqual(store.days().count, 0)
        XCTAssertFalse(FileManager.default.fileExists(atPath: dir.appendingPathComponent("\(header.start).json").path))
    }

    #if canImport(Darwin)
    func testTheFolderIsKeptOutOfTheBackup() throws {
        let store = try DayStore(dir: dir)
        XCTAssertEqual(try store.dir.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }
    #endif
}
