import XCTest
@testable import GudiData

/// The site's own data file decodes, with what Android also checks (DataTest).
final class SiteDataTests: XCTestCase {
    private let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()

    func testTheSitesRunsAndLiftsDecode() throws {
        let data = try Data(contentsOf: root.appendingPathComponent("site/data/runs-and-lifts.json"))
        let rl = try RunsAndLifts.decode(data)
        XCTAssertGreaterThan(rl.pistes.count, 20)
        XCTAssertGreaterThan(rl.lifts.count, 10)
        XCTAssertEqual(Set(rl.pistes.map(\.key)).count, rl.pistes.count, "piste keys are unique")
        XCTAssertTrue(rl.pistes.allSatisfy { ["green", "blue", "red", "black", "none"].contains($0.color) })
        // coordinates are [lat, lon] around Gudauri
        let p = try XCTUnwrap(rl.pistes.first { !$0.segs.isEmpty }?.segs.first?.g.first)
        XCTAssertEqual(p[0], 42.47, accuracy: 0.2); XCTAssertEqual(p[1], 44.47, accuracy: 0.2)
    }

    func testANewFieldOnTheSiteDoesNotBreakTheApp() throws {
        let json = #"{"pistes":[{"key":"A","color":"blue","newThing":1}],"lifts":[{"id":1,"name":"L","future":[]}],"extra":true}"#
        let rl = try RunsAndLifts.decode(Data(json.utf8))
        XCTAssertEqual(rl.pistes.first?.key, "A"); XCTAssertEqual(rl.lifts.first?.name, "L")
    }

    /// The lifts are getting `speed` (the cable's m/s) and a `research` object, and `dur` is being fixed in five of them
    /// (the architect's condition, 31.10.2026): a lift with them still decodes, and `dur` reads as a number or as text.
    func testALiftWithSpeedResearchAndANumericDurDecodes() throws {
        let json = #"""
        {"pistes":[],"lifts":[
          {"id":1,"name":"L","kind":"gondola","g":[[42.47,44.48],[42.48,44.49]],"len":1200,"dur":7.5,"speed":5,
           "research":{"conf":"medium","status":"fixed","notes":"x","sources":[{"url":"https://example.org","what":"dur"}]}},
          {"id":"2","name":"M","dur":"9","speed":null,"research":null}]}
        """#
        let rl = try RunsAndLifts.decode(Data(json.utf8))
        XCTAssertEqual(rl.lifts.map(\.name), ["L", "M"])
        XCTAssertEqual(rl.lifts[0].dur, "7.5"); XCTAssertEqual(rl.lifts[1].dur, "9")
        XCTAssertEqual(rl.lifts[0].len, 1200)
    }
}

/// The features behind which a new ability waits (docs/ARCHITECTURE.md m-8, answer 7).
final class ResortsTests: XCTestCase {
    func testFeatures() throws {
        let r = try Resorts.decode(Data(contentsOf: Repo.root.appendingPathComponent("site/data/resorts.json")))
        XCTAssertTrue(r.has("locate"))
        XCTAssertFalse(r.has("no-such-feature"))
        XCTAssertFalse(r.has("locate", in: "no-such-resort"))
    }
}
