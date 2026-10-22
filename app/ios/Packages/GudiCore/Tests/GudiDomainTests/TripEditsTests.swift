import XCTest
@testable import GudiDomain

/// The same cases as Android's trip/PassEditsTest.kt and trip/AirportsTest.kt (round 20, K-5).
final class TripEditsTests: XCTestCase {
    let d10 = Day(2027, 1, 10)!
    let d15 = Day(2027, 1, 15)!

    private func eq(_ a: (out: Day, ret: Day?), _ o: Day, _ r: Day?, line: UInt = #line) {
        XCTAssertEqual(a.out, o, line: line); XCTAssertEqual(a.ret, r, line: line)
    }

    func testTwoTaps() {
        // the first tap is the way out, the second the way back
        eq(PassEdits.tap(out: nil, ret: nil, day: d10), d10, nil)
        eq(PassEdits.tap(out: d10, ret: nil, day: d15), d10, d15)
        // a tap before the way out starts again from it; so does a third tap
        eq(PassEdits.tap(out: d15, ret: nil, day: d10), d10, nil)
        eq(PassEdits.tap(out: d10, ret: d15, day: d15), d15, nil)
        // the same day twice: out and back that day
        eq(PassEdits.tap(out: d10, ret: nil, day: d10), d10, d10)
    }

    func testANewTripFromTheCalendar() {
        let t = PassEdits.newTrip(out: d10, ret: d15, tlv: "TLV · Tel Aviv", tbs: "TBS · Tbilisi")
        XCTAssertEqual(t.out.fromCode, "TLV"); XCTAssertEqual(t.out.toCode, "TBS")
        XCTAssertEqual(t.ret?.fromCode, "TBS"); XCTAssertEqual(t.ret?.toCode, "TLV")
        // no times yet: the ski days are the days between the flights, as the calendar shades them
        XCTAssertEqual(t.skiDays(), Day(2027, 1, 11)!...Day(2027, 1, 14)!)
        XCTAssertNil(PassEdits.newTrip(out: d10, ret: nil, tlv: "TLV", tbs: "TBS").ret)
    }

    func testNewDatesKeepTheFlights() {
        let t = Trip(Leg(d10, flight: "6H 897", from: "TLV", to: "TBS", departs: ClockTime(16, 0), arrives: ClockTime(20, 35)), nil, ski: d10...d10)
        let n = t.withDates(Day(2027, 1, 12)!, Day(2027, 1, 17)!)
        XCTAssertEqual(n.out.flight, "6H 897"); XCTAssertEqual(n.out.arrives, ClockTime(20, 35))
        // a new return is the way out reversed; the ski days follow the flights again
        XCTAssertEqual(n.ret?.from, "TBS"); XCTAssertEqual(n.ret?.to, "TLV")
        XCTAssertNil(n.ski)
        XCTAssertNil(n.withDates(d10, nil).ret)
    }

    func testPlacesAndLegs() {
        let t = PassEdits.newTrip(out: d10, ret: d15, tlv: "TLV", tbs: "TBS").withPlaces(from: "TLV", to: "KUT · Kutaisi")
        XCTAssertEqual(t.ret?.fromCode, "KUT")
        let f = t.withLeg(ret: true) { $0.flight = "IZ 892" }
        XCTAssertEqual(f.ret?.flight, "IZ 892"); XCTAssertEqual(f.out.flight, "")
    }

    func testATypedCode() {
        XCTAssertEqual(Airports.code(" lca "), "LCA")
        XCTAssertNil(Airports.code("TB"))
        XCTAssertNil(Airports.code("Berlin"))
        XCTAssertNil(Airports.code("תלא"))
        XCTAssertNil(Airports.code("TL1"))
    }

    func testWhatTheFormSaves() {
        XCTAssertTrue(Airports.ok("TBS · טביליסי"))
        XCTAssertTrue(Airports.ok("LCA"))
        XCTAssertFalse(Airports.ok("Berlin Schönefeld"))
        XCTAssertFalse(Airports.ok("ברלין"))
        XCTAssertFalse(Airports.ok(""))
    }

    func testTheListAndItsSearch() {
        let names = ["TBS": "Tbilisi", "IST": "Istanbul", "SAW": "Istanbul"]
        let city = { (c: String) in names[c] ?? "" }
        XCTAssertEqual(Array(Airports.shown(first: ["TLV"], query: "", city: city).prefix(2)), ["TLV", "TBS"])
        XCTAssertEqual(Airports.shown(first: [], query: "istan", city: city), ["IST", "SAW"])
        XCTAssertEqual(Airports.shown(first: [], query: "kut", city: city), ["KUT"])
    }
}
