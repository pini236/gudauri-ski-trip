import XCTest
@testable import GudiData

/// The site's elevation model, read as Android reads it (data/Terrain.kt).
final class TerrainTests: XCTestCase {
    func testTheModelAndThePeaks() throws {
        let t = try Terrain.parse(Data(contentsOf: Repo.root.appendingPathComponent("site/data/terrain.json")))
        XCTAssertEqual(t.nx, 370); XCTAssertEqual(t.ny, 391)
        XCTAssertEqual(t.h.count, t.nx * t.ny)
        XCTAssertEqual(t.peaks.count, 6)
        // every peak stands on the model within a few tens of metres of its height
        for p in t.peaks { XCTAssertEqual(t.elev(p.x, p.y), Double(p.ele), accuracy: 120, p.name) }
        // the village of New Gudauri, about 2000 m
        XCTAssertEqual(t.elev(Geo.x(44.479), Geo.y(42.474)), 2000, accuracy: 250)
    }

    func testTheProjectionIsTheSites() {
        XCTAssertEqual(Geo.x(Geo.lon0), 0); XCTAssertEqual(Geo.y(Geo.lat0), 0)
        XCTAssertEqual(Geo.y(42.52), -1113.2, accuracy: 0.01)
    }
}

/// The repository's root, from this file's place in it (app/ios/Packages/GudiCore/Tests/<target>/<file>).
enum Repo {
    static let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
}
