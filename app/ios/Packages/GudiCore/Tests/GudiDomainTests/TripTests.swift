import XCTest
@testable import GudiDomain

/// The same cases as Android's TripTest (app/android/.../trip/TripTest.kt), so the three platforms count the same.
final class TripTests: XCTestCase {
    private func t(_ h: Int, _ m: Int) -> ClockTime { ClockTime(h, m)! }
    private func d(_ day: Int) -> Day { Day(2027, 1, day)! }
    private func at(_ day: Int, _ h: Int, _ m: Int = 0) -> LocalMoment { LocalMoment(d(day), h, m) }

    /// The group's trip (docs/STATUS.md): 10.1 16:00 → 20:35, back 15.1 01:35 → 02:15.
    private var group: Trip {
        Trip(Leg(d(10), flight: "6H 897", from: "TLV · תל אביב", to: "TBS · טביליסי", departs: t(16, 0), arrives: t(20, 35)),
             Leg(d(15), flight: "6H 892", from: "TBS · טביליסי", to: "TLV · תל אביב", departs: t(1, 35), arrives: t(2, 15)))
    }

    func testTheGroupSkisElevenToFourteen() {
        XCTAssertEqual(group.skiDays(), d(11)...d(14))
        XCTAssertEqual(group.skiDayCount(), 4)
    }

    func testSkiDayEdges() {
        let early = Trip(Leg(d(10), departs: t(5, 0), arrives: t(8, 30)), Leg(d(15), departs: t(19, 0)))
        XCTAssertEqual(early.skiDays(), d(10)...d(15))
        XCTAssertEqual(Trip(Leg(d(10), departs: t(6, 0), arrives: t(11, 59)), Leg(d(15))).skiDays()?.lowerBound, d(10))
        XCTAssertEqual(Trip(Leg(d(10), departs: t(6, 0), arrives: t(12, 0)), Leg(d(15))).skiDays()?.lowerBound, d(11))
        let night = Trip(Leg(d(10), departs: t(23, 0), arrives: t(3, 0)), Leg(d(15), departs: t(10, 0)))
        XCTAssertEqual(night.skiDays(), d(11)...d(14))
        XCTAssertNil(Trip(Leg(d(10))).skiDays())
        XCTAssertNil(Trip(Leg(d(10), arrives: t(20, 0)), Leg(d(11), departs: t(6, 0))).skiDays())
    }

    func testCountdownToTheMidnightOfTheFlightDayAsOnTheSite() {
        XCTAssertEqual(group.daysToFlight(at(9, 0, 1)), 1)
        XCTAssertEqual(group.daysToFlight(at(9, 23, 59)), 1)
        XCTAssertEqual(group.daysToFlight(LocalMoment(Day(2027, 1, 8)!, 23, 59)), 2)
        XCTAssertEqual(group.daysToFlight(at(9, 0, 0)), 1)
        XCTAssertEqual(group.daysToFlight(at(10, 0, 0)), 0)
        XCTAssertEqual(group.daysToFlight(at(10, 15, 59)), 0)
        XCTAssertEqual(group.daysToFlight(at(12, 9, 0)), 0)
        XCTAssertEqual(group.daysToFlight(LocalMoment(Day(2026, 10, 3)!, 12, 0)), 99)
    }

    func testSkiDayNOfMDuringTheTripAndNoCountAfterTheReturn() {
        XCTAssertEqual(group.stage(at(9, 12)), .before(days: 1))
        XCTAssertEqual(group.stage(at(10, 22)), .before(days: 0))
        XCTAssertEqual(group.stage(at(11, 0)), .skiDay(1, of: 4))
        XCTAssertEqual(group.stage(at(12, 9)), .skiDay(2, of: 4))
        XCTAssertEqual(group.stage(at(14, 23)), .skiDay(4, of: 4))
        XCTAssertEqual(group.stage(at(15, 1)), .skiDay(4, of: 4))
        XCTAssertEqual(group.stage(at(16, 0)), .over)
        let byHand = Trip(Leg(d(10)), ski: d(11)...d(12))
        XCTAssertEqual(byHand.stage(at(12, 18)), .skiDay(2, of: 2))
        XCTAssertEqual(byHand.stage(at(13, 8)), .over)
        XCTAssertEqual(Trip(Leg(d(10))).stage(at(20, 8)), .before(days: 0))
    }

    func testCodesAndCities() {
        XCTAssertEqual(group.out.fromCode, "TLV"); XCTAssertEqual(group.out.fromCity, "תל אביב")
        XCTAssertEqual(Leg(d(1), to: "tbs").toCode, "TBS")
        XCTAssertNil(Leg(d(1), to: "טביליסי").toCode); XCTAssertEqual(Leg(d(1), to: "טביליסי").toCity, "טביליסי")
        let l = Leg(d(10), from: "TLV · תל אביב", to: "TBS Tbilisi")
        XCTAssertEqual(l.toCode, "TBS"); XCTAssertEqual(l.toCity, "Tbilisi")
        XCTAssertNil(Leg(d(10), from: "Tel Aviv").fromCode); XCTAssertEqual(Leg(d(10), from: "Tel Aviv").fromCity, "Tel Aviv")
        XCTAssertNil(Leg(d(10), from: "tbs Tbilisi").fromCode)
    }

    func testSavedAndReadBack() {
        XCTAssertEqual(Trip.from(json: group.json()), group)
        let one = Trip(Leg(d(10)))
        XCTAssertEqual(Trip.from(json: one.json()), one)
        XCTAssertNil(Trip.from(json: Data("{oops".utf8)))
        XCTAssertNil(Trip.from(json: Data(#"{"v":2,"out":{"date":"2027-01-10"}}"#.utf8)))
        XCTAssertNil(Trip.from(json: nil))
        let mine = Trip(group.out, group.ret, ski: d(12)...d(13))
        XCTAssertEqual(mine.skiDayCount(), 2); XCTAssertEqual(mine.flightSkiDays(), d(11)...d(14))
        XCTAssertEqual(Trip.from(json: mine.json()), mine)
    }

    /// A trip saved by the Android app reads the same here (the formats are one).
    func testReadsAnAndroidTripAndTheParallelBuildsFormat() {
        let android = #"{"v":1,"out":{"date":"2027-01-10","flight":"6H 897","from":"TLV · תל אביב","to":"TBS · טביליסי","departs":"16:00","arrives":"20:35"},"ret":{"date":"2027-01-15","flight":"6H 892","from":"TBS · טביליסי","to":"TLV · תל אביב","departs":"01:35","arrives":"02:15"}}"#
        XCTAssertEqual(Trip.from(json: Data(android.utf8)), group)
        let old = #"{"v":1,"out":{"date":"2027-01-10","number":"GD 101","from":"TLV · Tel Aviv","to":"TBS · Tbilisi","departs":"16:00","arrives":"20:35"},"back":{"date":"2027-01-15","number":"GD 102","departs":"01:35","arrives":"02:15"}}"#
        let t = Trip.from(json: Data(old.utf8))!
        XCTAssertEqual(t.out.flight, "GD 101"); XCTAssertEqual(t.ret?.flight, "GD 102")
        XCTAssertEqual(t.skiDays(), d(11)...d(14))
    }

    func testDaysRoundTripTheCalendar() {
        XCTAssertEqual(Day(1970, 1, 1)!.epochDay, 0)
        XCTAssertEqual(Day(2024, 2, 29)!.plus(days: 1), Day(2024, 3, 1))
        XCTAssertNil(Day(2027, 2, 31))
        for z in stride(from: -800, through: 30_000, by: 37) { XCTAssertEqual(Day(epochDay: z).epochDay, z) }
    }
}
