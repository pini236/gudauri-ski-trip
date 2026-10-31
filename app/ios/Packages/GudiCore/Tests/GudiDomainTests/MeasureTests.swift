import XCTest
@testable import GudiDomain

final class MeasureTests: XCTestCase {
    /// The site's rule (site/js/telemetry.js, europe()): every Europe/ zone, five Atlantic islands and Cyprus.
    func testEuropeIsTheSitesZones() throws {
        for z in ["Europe/Berlin", "Europe/London", "Europe/Moscow", "Atlantic/Canary", "Atlantic/Reykjavik", "Asia/Nicosia", "Asia/Famagusta"] {
            XCTAssertTrue(Measure.europe(z), z)
        }
        for z in ["Asia/Jerusalem", "Asia/Tbilisi", "America/New_York", "Atlantic/Bermuda", "UTC", ""] {
            XCTAssertFalse(Measure.europe(z), z)
        }
        // the same list as the site's file, read from it, so a change there fails here
        let js = try String(contentsOf: Repo.root.appendingPathComponent("site/js/telemetry.js"), encoding: .utf8)
        XCTAssertTrue(js.contains("/^Europe\\//"))
        XCTAssertTrue(js.contains("/^Atlantic\\/(Azores|Canary|Faroe|Madeira|Reykjavik)$/"))
        XCTAssertTrue(js.contains("/^Asia\\/(Nicosia|Famagusta)$/"))
    }

    func testALinkSharedFromTheAppIsAShareLink() {
        XCTAssertEqual(Measure.source(of: URL(string: "https://gudauri-ski-trip.vercel.app/?utm_source=app&utm_medium=share#map/run/Tatra%202")!), "share_link")
        XCTAssertEqual(Measure.source(of: URL(string: "https://gudauri-ski-trip.vercel.app/#meet/158744075b")!), "link")
        XCTAssertEqual(Measure.source(of: URL(string: "https://gudauri-ski-trip.vercel.app/j/abc?utm_medium=whatsapp")!), "link")
        XCTAssertEqual(Measure.utm([URLQueryItem(name: "utm_source", value: "site"), URLQueryItem(name: "x", value: "1"),
                                    URLQueryItem(name: "utm_medium", value: " ")]), ["utm_source": "site"])
    }

    func testFirstOpenOnlyOnAFreshInstall() {
        XCTAssertTrue(Measure.firstOpen(launchRecorded: false, hadId: false, hadSwitch: false))
        XCTAssertFalse(Measure.firstOpen(launchRecorded: true, hadId: false, hadSwitch: false))
        XCTAssertFalse(Measure.firstOpen(launchRecorded: false, hadId: true, hadSwitch: false)) // an update
        XCTAssertFalse(Measure.firstOpen(launchRecorded: false, hadId: false, hadSwitch: true))
    }
}
