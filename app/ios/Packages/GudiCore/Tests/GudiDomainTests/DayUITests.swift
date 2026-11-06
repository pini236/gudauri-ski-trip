import XCTest
@testable import GudiDomain

/// The ski day's screens as values: the same cases as Android's LiveNumbersTest, SpeedScaleTest and DayModelTest.
final class DayUITests: XCTestCase {
    typealias L = DayRec.LiveNumbers

    func testSpeedIsWholeKmh() {
        XCTAssertEqual(L.kmh(nil), "0"); XCTAssertEqual(L.kmh(-1), "0")
        XCTAssertEqual(L.kmh(10), "36"); XCTAssertEqual(L.kmh(17.5), "63")
    }

    func testDistanceAndDrop() {
        XCTAssertEqual(L.km(nil), "0.0"); XCTAssertEqual(L.km(12_400), "12.4"); XCTAssertEqual(L.km(960), "1.0")
        XCTAssertEqual(L.metres(547.6), "548")
    }

    func testElapsedHasMinutesAndHours() {
        XCTAssertEqual(L.elapsed(ms: 12_000), "0:12"); XCTAssertEqual(L.elapsed(ms: 300_000), "5:00")
        XCTAssertEqual(L.elapsed(ms: 3_903_000), "1:05:03"); XCTAssertEqual(L.elapsed(ms: -5), "0:00")
    }

    func testWeakGpsAfterTwentySecondsWithoutAReading() {
        XCTAssertFalse(L.weakGps(now: 30_000, lastFix: 20_000, startedAt: 1_000))
        XCTAssertTrue(L.weakGps(now: 50_000, lastFix: 20_000, startedAt: 1_000))
        XCTAssertTrue(L.weakGps(now: 30_000, lastFix: 0, startedAt: 1_000))
        XCTAssertFalse(L.weakGps(now: 10_000, lastFix: 0, startedAt: 1_000))
        XCTAssertFalse(L.weakGps(now: 99_000, lastFix: 0, startedAt: 0))
    }

    func testTheFourStopsAreTheScale() {
        typealias S = DayRec.SpeedScale
        XCTAssertEqual(S.rgb(0), 0xF5D76E); XCTAssertEqual(S.rgb(20), 0xF08A24)
        XCTAssertEqual(S.rgb(40), 0xD62B2B); XCTAssertEqual(S.rgb(60), 0x7B2D9B)
        XCTAssertEqual(S.rgb(95), S.rgb(60)); XCTAssertEqual(S.rgb(nil), S.rgb(0)); XCTAssertEqual(S.rgb(-4), S.rgb(0))
        XCTAssertNotEqual(S.rgb(30), S.rgb(20)); XCTAssertNotEqual(S.rgb(30), S.rgb(40))
    }

    // MARK: DayModel
    // 2027-01-12 09:00 in Tbilisi (UTC+4) is 05:00 UTC
    private let nine: Int64 = 1_799_730_000_000
    private let zone = TimeZone(identifier: "Asia/Tbilisi")!
    private func min(_ m: Int64) -> Int64 { m * 60_000 }

    private func analysis(_ segs: [DayRec.Segment]) -> DayRec.Analysis {
        var a = DayRec.Analysis(analysis: 2, v: 1, resort: "gudauri", mode: "day", points: 0, start: nine, end: nine + min(300), baro: nil,
                                coverage: nil, gaps: [], falls: [], segments: segs, stats: nil)
        a.stats = DayRec.Stats(descents: 2, lifts: 1, skiDistM: 4200.5, dropM: 900, skiS: 0, liftS: 0, liftRiseM: 0, maxKmh: 63.4,
                               maxAt: DayRec.Spot(t: nine + min(5), lat: 42.5, lon: 44.5))
        return a
    }

    private func descent(_ t0: Int64, _ runs: [String], _ max: Double, kind: String = "run") -> DayRec.Segment {
        var s = DayRec.Segment(type: "descent", t0: t0, t1: t0 + min(4))
        s.legs = [DayRec.Leg(t0: t0, t1: t0 + min(4), kind: kind, runs: runs, distM: 1500)]
        s.maxKmh = max; s.dropM = 300; s.distM = 1500; s.offpiste = []
        return s
    }

    private func stop(_ kind: String, _ t0: Int64, _ t1: Int64) -> DayRec.Segment { var s = DayRec.Segment(type: "stop", t0: t0, t1: t1); s.kind = kind; return s }

    func testSummaryComesFromTheStats() {
        let d = DayRec.DayModel.build(analysis([]), zone: zone)
        XCTAssertEqual(d.summary.descents, 2); XCTAssertEqual(d.summary.dropM, 900); XCTAssertEqual(d.summary.maxKmh, 63.4)
        XCTAssertEqual(d.summary.maxAt, nine + min(5))
    }

    func testASharedStretchIsOneNameWithBothNumbers() throws {
        let d = DayRec.DayModel.build(analysis([descent(nine, ["Tatra 1", "Tatra 2"], 44)]), zone: zone)
        guard case .descent(let c) = try XCTUnwrap(d.chapters.first?.items.first) else { return XCTFail() }
        XCTAssertEqual(c.runs, ["Tatra 1 · Tatra 2"]); XCTAssertFalse(c.offRuns)
    }

    func testALiftShowsOnlyAsAQueue() throws {
        var lift1 = DayRec.Segment(type: "lift", t0: nine + min(2), t1: nine + min(10)); lift1.name = "Goodaura"
        var lift2 = DayRec.Segment(type: "lift", t0: nine + min(31), t1: nine + min(40)); lift2.name = "Kudebi"
        let d = DayRec.DayModel.build(analysis([stop("queue", nine, nine + min(2)), lift1, stop("queue", nine + min(20), nine + min(31)), lift2]), zone: zone)
        let items = try XCTUnwrap(d.chapters.first).items
        XCTAssertEqual(items, [.queue(t0: nine + min(20), minutes: 11, lift: "Kudebi")])
    }

    func testOffTheRunsIsSaidAndNeverNamed() throws {
        let d = DayRec.DayModel.build(analysis([descent(nine, [], 38, kind: "off")]), zone: zone)
        guard case .descent(let c) = try XCTUnwrap(d.chapters.first?.items.first) else { return XCTFail() }
        XCTAssertTrue(c.offRuns); XCTAssertTrue(c.runs.isEmpty)
    }

    func testTheAfternoonIsAChapterOfItsOwn() {
        let d = DayRec.DayModel.build(analysis([descent(nine, ["Tatra 1"], 40), descent(nine + min(300), ["Kikilo 1"], 41)]), zone: zone)
        XCTAssertEqual(d.chapters.map(\.afternoon), [false, true]); XCTAssertEqual(d.chapters.map(\.descents), [1, 1])
    }

    func testShortRestsAndShortWalksAreLeftOut() {
        var walk = DayRec.Segment(type: "walk", t0: nine + min(70), t1: nine + min(72)); walk.distM = 40
        let d = DayRec.DayModel.build(analysis([stop("rest", nine, nine + min(4)), stop("long", nine + min(10), nine + min(60)), walk]), zone: zone)
        XCTAssertEqual(d.chapters.first?.items, [.rest(t0: nine + min(10), minutes: 50, long: true)])
        XCTAssertNil(DayRec.DayModel.build(analysis([]), zone: zone).chapters.first)
    }
}
