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
