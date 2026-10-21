import XCTest
@testable import GudiDomain

/// The home page's sky, as on the site and Android (DayNightTest): the same images and darkness through a January day.
final class DayNightTests: XCTestCase {
    private func gud(_ h: Int, _ m: Int = 0) -> Int64 {
        Int64(Day(2027, 1, 12)!.epochDay) * 86_400_000 + Int64((h - 4) * 3600 + m * 60) * 1000
    }
    private func local(_ ms: Int64) -> (day: Day, h: Int, m: Int) {
        let s = ms / 1000 + 4 * 3600
        return (Day(epochDay: Int(s / 86_400)), Int(s % 86_400 / 3600), Int(s % 3600 / 60))
    }

    func testThroughTheDay() {
        let noon = DayNight.at(gud(12, 30), .auto)
        XCTAssertFalse(noon.dark); XCTAssertTrue(["morning", "noon"].contains(noon.imgA)); XCTAssertEqual(noon.clock, "12:30")
        let night = DayNight.at(gud(23), .auto)
        XCTAssertTrue(night.dark); XCTAssertEqual(night.imgA, "night"); XCTAssertEqual(night.stars, 1)
        let dawn = DayNight.at(gud(7, 30), .auto)
        XCTAssertTrue(dawn.imgA == "dawn" || dawn.imgB == "dawn")
        let dusk = DayNight.at(gud(17, 30), .auto)
        XCTAssertTrue([dusk.imgA, dusk.imgB].contains { $0 == "sunset" || $0 == "dusk" })
    }

    func testTheModesOverrideTheClock() {
        XCTAssertFalse(DayNight.at(gud(23), .day).dark)
        XCTAssertTrue(DayNight.at(gud(12), .night).dark)
        XCTAssertEqual(DayNight.at(gud(23), .day).clock, "23:00")
        XCTAssertEqual(DayNight.Mode.auto.next, .day); XCTAssertEqual(DayNight.Mode.night.next, .auto)
    }

    func testTheMountainIsLitAsTheSwitchSays() {
        let at = gud(23)
        XCTAssertEqual(DayNight.lightTime(at, .auto), at)
        let night = local(DayNight.lightTime(at, .night))
        XCTAssertEqual(night.h, 22); XCTAssertEqual(night.m, 0); XCTAssertEqual(night.day, Day(2027, 1, 12))
        let day = local(DayNight.lightTime(at, .day))
        XCTAssertTrue((12...13).contains(day.h), "solar noon in Gudauri is about 13:00 local time")
    }

    func testTheSitesSunMath() throws {
        let app = try String(contentsOf: Repo.root.appendingPathComponent("site/js/app.js"), encoding: .utf8)
        XCTAssertTrue(app.contains("9.87"), "the equation of time, as on the site")
        let t = Sun.times(gud(12))
        // the same formula as Android's Sky.sunTimes, worked out by hand for 12.1.2027
        XCTAssertEqual(t.rise, 8.6025, accuracy: 0.001); XCTAssertEqual(t.set, 17.7380, accuracy: 0.001); XCTAssertEqual(t.noon, 13.1702, accuracy: 0.001)
    }
}
