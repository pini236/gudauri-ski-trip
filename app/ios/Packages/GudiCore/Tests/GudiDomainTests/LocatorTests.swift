import XCTest
import GudiData
@testable import GudiDomain

/// "Where am I" against the shared cases (tools/fixtures/location-m7.json), which the site and Android read too.
final class LocatorTests: XCTestCase {
    func testEveryCase() throws {
        let fx = try JSONSerialization.jsonObject(with: Data(contentsOf: Repo.root.appendingPathComponent("tools/fixtures/location-m7.json"))) as! [String: Any]
        let runs = try RunsAndLifts.decode(Data(contentsOf: Repo.root.appendingPathComponent("site/data/runs-and-lifts.json")))
        let terrain = try Terrain.parse(Data(contentsOf: Repo.root.appendingPathComponent("site/data/terrain.json")))
        for c in fx["cases"] as! [[String: Any]] {
            var loc = Locator(runs: runs, terrain: terrain)
            for r in c["readings"] as! [[String: Any]] {
                let got = loc.feed(x: Geo.x((r["lon"] as! NSNumber).doubleValue), y: Geo.y((r["lat"] as! NSNumber).doubleValue),
                                   accuracy: (r["accuracy"] as! NSNumber).doubleValue, approximate: r["approximate"] as? Bool ?? false)
                let e = r["expect"] as! [String: String]
                let want: Locator.Fix
                switch e["state"]! {
                case "run": want = .onRun(e["run"]!)
                case "lift": want = .onLift(e["lift"]!)
                case "outside": want = .outside
                case "approx": want = .approx
                case "low": want = .low
                default: want = .free
                }
                XCTAssertEqual(got, want, c["name"] as! String)
            }
        }
    }
}
