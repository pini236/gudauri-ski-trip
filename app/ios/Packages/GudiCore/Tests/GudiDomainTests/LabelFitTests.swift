import XCTest
@testable import GudiData
@testable import GudiDomain

final class LabelFitTests: XCTestCase {
    private let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()

    private func site() throws -> RunsAndLifts {
        try RunsAndLifts.decode(Data(contentsOf: root.appendingPathComponent("site/data/runs-and-lifts.json")))
    }

    func testTheSitesLabelSharedAndOpenFieldsDecode() throws {
        let rl = try site()
        let named = rl.pistes.filter(\.named)
        XCTAssertGreaterThan(named.filter { !$0.labelWindows.isEmpty }.count, 10)
        XCTAssertTrue(rl.pistes.contains { !$0.sharedWindows.isEmpty && $0.sharedWindows.allSatisfy { $0.other != nil } })
        let w = try XCTUnwrap(rl.pistes.first { $0.key == "Soliko 1" }?.labelWindows.first)
        XCTAssertNil(w.other); XCTAssertGreaterThan(w.t1, w.t0)
    }

    func testAnUnknownVersionReadsAsNoWindows() throws {
        let json = #"{"pistes":[{"key":"A","label":{"v":2,"a":[[0,1,2,3,4]]},"shared":{"v":1,"a":[[0,10,20,30,40,"B"]]}}],"lifts":[]}"#
        let p = try XCTUnwrap(RunsAndLifts.decode(Data(json.utf8)).pistes.first)
        XCTAssertTrue(p.labelWindows.isEmpty)
        XCTAssertEqual(p.sharedWindows, [LabelWindow(s: 0, t0: 10, t1: 20, cL: 30, cR: 40, other: "B")])
    }

    func testOpenEndsMatchTheSharedFixtureAndDrawFromMediumUp() throws {
        let fx = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("tools/fixtures/labels-open.json"))) as! [String: Any]
        let want = fx["open"] as! [String: [[String: String]]]
        let rl = try site()
        for (k, ends) in want {
            let p = try XCTUnwrap(rl.pistes.first { $0.key == k }, k)
            XCTAssertEqual(p.openEnds.map(\.at), ends.map { $0["at"]! }, k)
            XCTAssertEqual(p.openEnds.map(\.conf), ends.map { $0["conf"]! }, k)
            // a low open end is kept in the data and never drawn
            let drawn = OpenEnds.fades(p) { x, y in -y }
            XCTAssertEqual(drawn.isEmpty, ends.allSatisfy { $0["conf"] == "low" }, k)
        }
    }

    func testFitPicksTheSideWithRoomAndReadsLeftToRight() {
        let line: [LabelFit.P] = (0...8).map { (Double($0) * 100, 0) }
        let w = LabelWindow(s: 0, t0: 500, t1: 1000, cL: 10, cR: 140)
        // left (above) has less than the 16 m a 10 m text 6 m off the line needs, so it goes below
        let f = LabelFit.fit([w], segs: [line], wM: 100, hM: 10, off: 6)
        XCTAssertEqual(f?.side, "R")
        XCTAssertTrue(f!.pts.allSatisfy { $0.y > 0 })
        XCTAssertLessThan(f!.pts.first!.x, f!.pts.last!.x)
        XCTAssertNil(LabelFit.fit([w], segs: [line], wM: 600, hM: 10, off: 6), "a window shorter than the name is skipped")
        XCTAssertEqual(LabelFit.fit([LabelWindow(s: 0, t0: 500, t1: 1000, cL: 255, cR: 255)], segs: [line], wM: 100, hM: 10, off: 6)?.side, "L")
    }

    func testASharedStretchOfOneFamilyIsNamedOnce() {
        XCTAssertEqual(LabelFit.sharedName("Tatra 2", "Tatra 1"), "Tatra 1 · 2")
        XCTAssertEqual(LabelFit.sharedName("Tatra 1", "Goodaura 2"), "Tatra 1")
        XCTAssertEqual(LabelFit.sharedName("Shino", "Tatra 1"), "Shino")
    }

    func testArrowsStartAt90MetresThenEvery150() {
        let segs = (0..<100).map { i in (a: (x: Double(i) * 10, y: 0.0), b: (x: Double(i + 1) * 10, y: 0.0)) }
        let a = RunArrows.along(segs)
        XCTAssertEqual(a.map { Int($0.x) }, [90, 240, 390, 540, 690, 840])
        XCTAssertEqual(a[0].angle, 0, accuracy: 1e-9)
        XCTAssertEqual(RunArrows.step(metresPerPx: 5), 2)
    }
}
