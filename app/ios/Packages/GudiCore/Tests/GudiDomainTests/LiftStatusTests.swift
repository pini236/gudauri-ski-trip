import XCTest
import GudiData
@testable import GudiDomain

/// The same cases as Android's LiftStatusTest: the site's /api/status, read the way the site does.
final class LiftStatusTests: XCTestCase {
    private let t0: Int64 = 1_799_740_800_000 // 2027-01-12T08:00:00Z
    private let sample = #"{"updated":"2027-01-12T08:00:00Z","lifts":{"Goodaura":{"open":true},"Sadzele":{"open":false,"reason":"wind"},"Kudebi":{"open":false}},"pistes":{"Tatra 2":{"open":false},"Kudebi 1":{"open":true}}}"#
    private func report(_ s: String) -> Report { Report.parse(Data(s.utf8))! }

    func testTheSitesRules() throws {
        let app = try String(contentsOf: Repo.root.appendingPathComponent("site/js/app.js"), encoding: .utf8)
        XCTAssertTrue(app.contains("STALE=30*6e4"), "30 minutes, as on the site")
        XCTAssertTrue(app.contains("fetch('api/status'"), "the same file")
        XCTAssertTrue(app.contains("setInterval(load,5*6e4)"), "every five minutes")
        XCTAssertEqual(LiftStatus.staleMs, 30 * 60_000)
        XCTAssertEqual(LiftStatus.everyMs, 5 * 60_000)
    }

    func testAReportNeedsATimeAndLifts() {
        XCTAssertNil(Report.parse(Data(#"{"lifts":{}}"#.utf8)))
        XCTAssertNil(Report.parse(Data(#"{"updated":"2027-01-12T08:00:00Z"}"#.utf8)))
        XCTAssertNil(Report.parse(Data("<html>not found</html>".utf8)))
        XCTAssertEqual(report(#"{"updated":"2027-01-12T12:00:00+04:00","lifts":{}}"#).updated, t0)
        let r = report(sample)
        XCTAssertEqual(r.updated, t0)
        XCTAssertEqual(r.lifts["Sadzele"]?.reason, "wind")
        XCTAssertNil(r.lifts["Kudebi"]?.reason)
        XCTAssertEqual(r.pistes["Tatra 2"], false)
    }

    func testOlderThanHalfAnHourIsNoInformation() {
        let names = ["Goodaura", "Sadzele", "Kudebi", "Zuma"]
        let fresh = LiftStatus(names: names, report: report(sample), now: t0 + 29 * 60_000)
        XCTAssertTrue(fresh.fresh)
        XCTAssertEqual(fresh.isOpen("Goodaura"), true)
        XCTAssertEqual(fresh.isOpen("Sadzele"), false)
        XCTAssertNil(fresh.isOpen("Zuma"))
        XCTAssertEqual(fresh.open, 1)
        XCTAssertEqual(fresh.reason("Sadzele"), "wind")
        let stale = LiftStatus(names: names, report: report(sample), now: t0 + 31 * 60_000)
        XCTAssertFalse(stale.fresh)
        XCTAssertNil(stale.isOpen("Goodaura"))
        XCTAssertNil(stale.reason("Sadzele"))
        XCTAssertEqual(stale.open, 0)
        XCTAssertFalse(LiftStatus(names: names, report: nil, now: t0).fresh)
        XCTAssertEqual(LiftStatus(names: names, report: report(sample), now: t0 + 6 * 60_000 + 20_000).minutesAgo, 6)
    }

    func testARunIsOpenForMeOnlyWithALiftUpToIt() {
        let s = LiftStatus(names: [], report: report(sample), now: t0)
        XCTAssertEqual(s.runOpen(Piste(key: "Tatra 2", fromLifts: ["Goodaura"])), false)
        XCTAssertEqual(s.runOpen(Piste(key: "Soliko 1", fromLifts: ["Goodaura", "Sadzele"])), true)
        XCTAssertEqual(s.runOpen(Piste(key: "Kudebi 1", fromLifts: ["Kudebi"])), false)
        XCTAssertNil(s.runOpen(Piste(key: "Zuma 1", fromLifts: ["Zuma"])))
        XCTAssertNil(LiftStatus(names: [], report: report(sample), now: t0 + 31 * 60_000).runOpen(Piste(key: "Tatra 2")))
    }

    func testWhatChangedSinceYouChecked() {
        let names = ["Goodaura", "Sadzele", "Kudebi", "Zuma"]
        let s = LiftStatus(names: names, report: report(sample), now: t0)
        XCTAssertTrue(s.changes(nil).isEmpty)
        let before: [String: Bool?] = ["Goodaura": false, "Sadzele": false, "Kudebi": true, "Zuma": true]
        let c = s.changes(before)
        XCTAssertEqual(c.map(\.0), ["Goodaura", "Kudebi"]); XCTAssertEqual(c.map(\.1), [true, false])
        let snap = s.snapshot()
        XCTAssertEqual(snap["Goodaura"], .some(true)); XCTAssertEqual(snap["Kudebi"], .some(false))
        XCTAssertEqual(snap["Zuma"], .some(nil))
    }

    func testTheBoardHasTheSitesLifts() throws {
        let rl = try RunsAndLifts.decode(Data(contentsOf: Repo.root.appendingPathComponent("site/data/runs-and-lifts.json")))
        let names = LiftStatus.names(rl.mainLifts)
        XCTAssertEqual(names.count, 12)
        XCTAssertTrue(["Goodaura", "New Goodaura", "Sadzele", "Kudebi", "Snow Park"].allSatisfy(names.contains))
        XCTAssertGreaterThanOrEqual(rl.pistes.filter { !$0.fromLifts.isEmpty }.count, 20)
    }
}
