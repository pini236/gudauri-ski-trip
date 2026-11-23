import XCTest
import GudiData
@testable import GudiDomain

/// Rounds 39 and 40 against the shared cases the site and Android read too (tools/fixtures/season-countdown.json, skipass-calc.json).
final class SeasonPricesTests: XCTestCase {
    private func json(_ name: String) throws -> [String: Any] {
        try JSONSerialization.jsonObject(with: Data(contentsOf: Repo.root.appendingPathComponent("tools/fixtures/\(name)"))) as! [String: Any]
    }

    func testCountdownCases() throws {
        let fx = try json("season-countdown.json")
        let tz = TimeZone(identifier: fx["tz"] as! String)!
        let iso = ISO8601DateFormatter()
        for c in fx["cases"] as! [[String: Any]] {
            let name = c["name"] as! String
            let raw = (c["season"] as? [String: Any]).map { Resorts.SeasonRaw(open: $0["open"] as? String, announced: $0["announced"] as? String) }
            let season = Season.from(raw, tz: tz.identifier)
            let now = iso.date(from: c["now"] as! String)!
            let s = Season.decide(season, today: Season.today(now, tz: tz), fresh: (c["report"] as? String) == "fresh",
                                  seenOpen: Season.localDate(c["seen_open"] as? String))
            let e = c["expect"] as! [String: Any]
            let kind: Season.Kind = ["countdown": .countdown, "opening_day": .openingDay, "no_info": .noInfo, "live": .live, "none": .none][e["state"] as! String]!
            XCTAssertEqual(s.kind, kind, name)
            XCTAssertEqual(s.days, e["days"] as? Int, name)
            XCTAssertEqual(s.postponed, e["postponed"] as! Bool, name)
        }
    }

    func testPriceCases() throws {
        let fx = try json("skipass-calc.json")
        let file = try Data(contentsOf: Repo.root.appendingPathComponent("site/data/prices.json"))
        let prices = try XCTUnwrap(Prices.parse(file))
        // the fixture's table is the file's table
        let table = (fx["passes"] as! [[String: Any]]).map { [$0["days"] as! Int, $0["adult"] as! Int, $0["child"] as! Int] }
        XCTAssertEqual(prices.passes.map { [$0.days, $0.adult, $0.child] }, table)
        for c in fx["cases"] as! [[String: Any]] {
            let days = c["days"] as! Int
            for (key, child) in [("adult", false), ("child", true)] {
                let q = prices.price(days: days, child: child)
                if let want = c[key] as? [String: Any] {
                    XCTAssertEqual(q?.total, want["total"] as? Int, "\(key) \(days)")
                    XCTAssertEqual(q?.parts, want["parts"] as? [Int], "\(key) \(days)")
                } else { XCTAssertNil(q, "\(key) \(days)") }
            }
        }
    }

    func testPriceFileRules() throws {
        let file = try Data(contentsOf: Repo.root.appendingPathComponent("site/data/prices.json"))
        let p = try XCTUnwrap(Prices.parse(file))
        XCTAssertTrue(p.valid(Day(2027, 4, 30)!))
        XCTAssertFalse(p.valid(Day(2027, 5, 1)!), "after `until` there is no price")
        XCTAssertEqual(p.buyIos, "https://apps.apple.com/app/id6698866856")
        XCTAssertNotNil(p.buyAndroid)
        // the link check: anything but the two stores, or with a campaign, is dropped
        XCTAssertNil(Prices.appleLink("https://apps.apple.com/app/id1?pt=123&ct=x"))
        XCTAssertNil(Prices.appleLink("https://evil.example/app/id1"))
        XCTAssertNil(Prices.appleLink("http://apps.apple.com/app/id1"))
        XCTAssertNotNil(Prices.appleLink("https://apps.apple.com/app/id1?hl=he"))
        XCTAssertNil(Prices.playLink("https://play.google.com/store/apps/details?id=a.b&referrer=utm_source%3Dx"))
        XCTAssertNotNil(Prices.playLink("https://play.google.com/store/apps/details?id=a.b&hl=en"))
        // another schema or currency is not this app's file
        var o = try JSONSerialization.jsonObject(with: file) as! [String: Any]
        o["schema"] = 2
        XCTAssertNil(Prices.parse(try JSONSerialization.data(withJSONObject: o)))
        o["schema"] = 1; o["currency"] = "USD"
        XCTAssertNil(Prices.parse(try JSONSerialization.data(withJSONObject: o)))
    }
}
