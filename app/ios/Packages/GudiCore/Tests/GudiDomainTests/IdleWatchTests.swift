import XCTest
@testable import GudiDomain

/// The day that stops on its own (m-8, condition 2), the same cases as Android's `IdleWatchTest`.
final class IdleWatchTests: XCTestCase {
    private let min: Int64 = 60_000

    func testMovingKeepsTheDayGoing() {
        var w = DayRec.IdleWatch()
        var t: Int64 = 0
        for i in 0..<120 {
            t += min
            XCTAssertEqual(w.feed(t, Double(i) * 100, 0), .fine)
        }
    }

    func testStillInsideTheResortStopsAfterAnHour() {
        var w = DayRec.IdleWatch()
        XCTAssertEqual(w.feed(0, 0, 0), .fine)
        XCTAssertEqual(w.feed(30 * min, 5, 5), .fine)
        if case .warn = w.feed(59 * min, 5, 5) {} else { XCTFail("a notice two minutes before") }
        XCTAssertEqual(w.feed(60 * min, 5, 5), .stop(outside: false))
    }

    func testStillOutsideStopsAfterTwentyMinutes() {
        var w = DayRec.IdleWatch()
        let x = DayRec.IdleWatch.area.x1 + 5000, y = 0.0
        XCTAssertEqual(w.feed(0, x, y), .fine)
        XCTAssertEqual(w.feed(10 * min, x, y), .fine)
        XCTAssertEqual(w.feed(20 * min, x, y), .stop(outside: true))
    }

    func testAWalkToTheLiftIsNotStillness() {
        var w = DayRec.IdleWatch()
        XCTAssertEqual(w.feed(0, 0, 0), .fine)
        // more than 30 m from where the stillness began: the clock starts again
        XCTAssertEqual(w.feed(55 * min, 100, 0), .fine)
        XCTAssertEqual(w.feed(60 * min, 100, 0), .fine)
        XCTAssertEqual(w.feed(115 * min, 100, 0), .stop(outside: false))
    }

    func testTheResortIsInside() {
        XCTAssertTrue(DayRec.IdleWatch.inside(0, 0))
        XCTAssertFalse(DayRec.IdleWatch.inside(DayRec.IdleWatch.area.x1 + 1, 0))
        XCTAssertFalse(DayRec.IdleWatch.inside(0, DayRec.IdleWatch.area.y0 - 1))
    }
}
