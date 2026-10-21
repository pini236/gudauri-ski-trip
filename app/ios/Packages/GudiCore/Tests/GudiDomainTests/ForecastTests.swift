import XCTest
@testable import GudiDomain

/// Weather by altitude against the shared readings (tools/fixtures/weather-m6.json, m-6), which the site and Android read too.
final class ForecastTests: XCTestCase {
    var fixture: [String: Any] = [:]
    var f: Forecast!
    var cases: [String: Any] { fixture["cases"] as! [String: Any] }

    override func setUpWithError() throws {
        let d = try Data(contentsOf: Repo.root.appendingPathComponent("tools/fixtures/weather-m6.json"))
        fixture = try JSONSerialization.jsonObject(with: d) as! [String: Any]
        f = Forecast.parse(try JSONSerialization.data(withJSONObject: fixture["answer"]!))
        XCTAssertNotNil(f)
    }

    private func now(_ o: [String: Any]) -> Forecast.Now {
        func i(_ k: String) -> Int? { (o[k] as? NSNumber)?.intValue }
        return Forecast.Now(temp: i("temp"), wind: i("wind"), gust: i("gust"), dir: i("dir"), snow24: i("snow24"))
    }

    func testFreshStaleAndNoneByAge() {
        for c in cases["state"] as! [[String: Any]] {
            let at = Forecast.millis(local: c["now"] as! String)!
            XCTAssertEqual(f.state(now: at).rawValue, c["state"] as? String, c["now"] as! String)
        }
    }

    func testTheHourThatHasBegun() {
        for c in cases["now"] as! [[String: Any]] {
            XCTAssertEqual(f.now(c["point"] as! String, at: c["now"] as! String), now(c["expect"] as! [String: Any]), "\(c)")
        }
        XCTAssertNil(f.now("goodaura", at: "2027-01-12T12:10")!.temp)
        XCTAssertNil(f.now("village", at: "2027-01-20T10:00"))
    }

    func testAtTheAltitudeOfARunsTopAndBottom() {
        for c in cases["altitude"] as! [[String: Any]] {
            XCTAssertEqual(f.at(alt: (c["alt"] as! NSNumber).doubleValue, at: c["now"] as! String), now(c["expect"] as! [String: Any]), "\(c)")
        }
    }

    func testTheServersRiskForADay() {
        for c in cases["risk"] as! [[String: Any]] {
            let e = c["expect"] as! [String: String]
            for lift in Forecast.riskLifts { XCTAssertEqual(f.risk(lift, date: c["date"] as! String), e[lift]) }
        }
        XCTAssertNil(f.risk("Sadzele", date: "2027-03-01"))
    }

    func testABrokenOrOtherAnswerIsNone() {
        XCTAssertNil(Forecast.parse(Data("{}".utf8)))
        XCTAssertNil(Forecast.parse(Data(#"{"schema":2}"#.utf8)))
        XCTAssertNil(Forecast.parse(Data("<html>".utf8)))
        XCTAssertNotNil(f.day("goodaura", date: "2027-01-14"))
        XCTAssertEqual(f.lastDay, "2027-01-26")
    }
}
