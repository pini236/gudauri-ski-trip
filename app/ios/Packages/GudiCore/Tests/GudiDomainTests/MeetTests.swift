import XCTest
import GudiData
@testable import GudiDomain

/// The same cases as Android's MeetTest: the expected stations and routes were printed by the site's own code
/// (site/js/app.js, MEET) on the real data, so a meetup saved on the site shows the same place in the app.
final class MeetTests: XCTestCase {
    static let data = Repo.root.appendingPathComponent("site/data")
    static let relief: Relief = try! Relief.parse(Data(contentsOf: data.appendingPathComponent("terrain.json")))
    static let runs: RunsAndLifts = try! RunsAndLifts.decode(Data(contentsOf: data.appendingPathComponent("runs-and-lifts.json")))
    static let plan = MeetPlan.build(runs, relief)
    var plan: MeetPlan { Self.plan }

    func testTheStationsAreTheSitesOnes() {
        let site = [
            ("158744055b", "Snow Park:b Goodaura:t"), ("158744055t", "Snow Park:t"), ("158744059b", "Pirveli:b"),
            ("158744059t", "Pirveli:t Soliko:b"), ("158744060b", "Sadzele:b"), ("158744060t", "Sadzele:t"), ("158744063b", "Khada:b"),
            ("158744063t", "Khada:t"), ("158744075b", "Goodaura:b New Goodaura:b"), ("158744077t", "Soliko:t"), ("158817616b", "Zuma:b"),
            ("158817616t", "Zuma:t"), ("472374253b", "Shino:b"), ("472374253t", "Shino:t Firni:b"), ("663639361b", "Kudebi:b"),
            ("663639361t", "Kudebi:t"), ("663643826t", "Firni:t"), ("1196532543b", "Kikilo:b"), ("1196532543t", "Kikilo:t"),
            ("1196532544t", "New Goodaura:t"),
        ]
        let got = plan.stations.map { s in (s.id, s.ends.map { $0.lift + ($0.top ? ":t" : ":b") }.joined(separator: " ")) }
        XCTAssertEqual(got.map(\.0), site.map(\.0))
        XCTAssertEqual(got.map(\.1), site.map(\.1))
        // the name is the first lift that starts there: the top of Goodaura is "Snow Park", the top of Shino is "Firni"
        XCTAssertEqual(plan.byId["158744055b"]?.name, "Snow Park")
        XCTAssertEqual(plan.byId["472374253t"]?.name, "Firni")
        XCTAssertEqual(Double(plan.byId["158744075b"]!.h!), 2161, accuracy: 1)
        XCTAssertEqual(Double(plan.byId["158744060t"]!.h!), 3235, accuracy: 1)
    }

    func testTheThreeSpotsInOneTap() {
        XCTAssertEqual(plan.preset(.morning)?.id, "158744075b")
        XCTAssertEqual(plan.preset(.noon)?.id, "158744055b")
        XCTAssertEqual(plan.preset(.end)?.id, "158744075b")
    }

    func testHowToGetThereComesFromTheConnections() {
        func ways(_ id: String) -> [String] {
            plan.ways(plan.byId[id]!).map { w in
                let from: String
                switch w.from { case .run(let k): from = "run:" + k; case .topOf(let k): from = "top:" + k; case .bottomOf(let l): from = "bottom:" + l }
                return from + " " + w.sameAs
            }
        }
        XCTAssertEqual(ways("158744075b"), ["run:Goodaura 1 r:Goodaura 1|r:Goodaura 2", "run:Snow Park r:Snow Park|r:Goodaura 1", "run:Tatra 1 r:Tatra 1|r:Shino"])
        XCTAssertEqual(ways("472374253t"), ["bottom:Shino l:Shino", "run:Sadzele 3 r:Sadzele 3|r:Snow Park", "run:Firni 2 r:Firni 2|r:Baby", "run:Firni 1 r:Firni 1|r:Firni 2"])
        XCTAssertEqual(ways("158744063b"), ["top:Khada r:Khada", "run:Kudebi 1 r:Kudebi 1|r:Kudebi 2"])
        XCTAssertEqual(ways("1196532544t"), ["bottom:New Goodaura l:New Goodaura"])
    }

    func testTheDaysAreYourTripsSkiDaysOrTheNextWeek() {
        let now: Int64 = 1_790_976_600_000 // 2026-10-02T21:30:00Z, already the 3rd in Gudauri
        let none = Meet.days(nil, nowMs: now)
        XCTAssertEqual(none.first, Day(2026, 10, 3)); XCTAssertEqual(none.count, 7)
        let trip = Trip(Leg(Day(2027, 1, 10)!, flight: "GD 101", from: "TLV", to: "TBS", departs: ClockTime(16, 0), arrives: ClockTime(20, 35)),
                        Leg(Day(2027, 1, 15)!, flight: "GD 102", from: "TBS", to: "TLV", departs: ClockTime(1, 35), arrives: ClockTime(2, 15)))
        XCTAssertEqual(Meet.days(trip, nowMs: now), (11...14).map { Day(2027, 1, $0)! })
        // a trip with only the way there: the week after landing
        let out = Trip(Leg(Day(2027, 1, 10)!, from: "TLV", to: "TBS"))
        XCTAssertEqual(Meet.days(out, nowMs: now).first, Day(2027, 1, 11))
    }

    func testTheCountdownIsInGudauriTime() {
        let at = Meet.at(Day(2027, 1, 11)!, ClockTime(9, 30)!) // 05:30 UTC
        XCTAssertEqual(at, 1_799_645_400_000)
        let min: Int64 = 60_000
        XCTAssertEqual(Meet.left(at: at, now: at - 30 * min), .minutes(30))
        XCTAssertEqual(Meet.left(at: at, now: at - 72 * min), .hours("1:12"))
        XCTAssertEqual(Meet.left(at: at, now: at - (25 * 60 + 30) * min), .days(2))
        XCTAssertEqual(Meet.left(at: at, now: at + min), .passed)
    }

    func testTheLinkIsTheSitesAndReadsBack() {
        let link = Meet.link("158744075b", ClockTime(9, 30)!, Day(2027, 1, 11)!)
        XCTAssertEqual(link, "https://gudauri-ski-trip.vercel.app/#meet/158744075b/0930/20270111")
        let r = MeetLink.parse(url: link)!
        XCTAssertEqual(r.station, "158744075b")
        XCTAssertEqual(Meet.time(r.time), ClockTime(9, 30)); XCTAssertEqual(Meet.day(r.day), Day(2027, 1, 11))
        XCTAssertEqual(MeetLink.parse(url: Meet.sharedLink("158744075b", ClockTime(9, 30)!, Day(2027, 1, 11)!)), r)
        XCTAssertNil(Meet.time("2561")); XCTAssertNil(Meet.day("20271341"))
        XCTAssertEqual(MeetLink.parse(path: "#meet"), MeetLink())
        XCTAssertNil(MeetLink.parse(path: "meet/x/93/2027"))
        XCTAssertNil(MeetLink.parse(url: "https://example.com/#meet/158744075b/0930/20270111"))
        XCTAssertEqual(MeetLink.parse(route: "158744075b"), MeetLink(station: "158744075b"))
        XCTAssertEqual(MeetLink.parse(route: "158744075b/0930/20270111"), r)
    }

    /// K-3: a meetup in the group opens its card, at its station, day and time in Gudauri.
    func testAGroupMeetupOpensItsCard() {
        let at = Meet.at(Day(2027, 1, 11)!, ClockTime(9, 30)!)
        XCTAssertEqual(Meet.card("158744075b", atMs: at), "158744075b/0930/20270111")
        XCTAssertEqual(MeetLink.parse(route: Meet.card("158744075b", atMs: at)), MeetLink(station: "158744075b", time: "0930", day: "20270111"))
        // 23:50 in Gudauri is still that day, though it is already the next day nowhere east of it
        XCTAssertEqual(Meet.card("s", atMs: Meet.at(Day(2027, 1, 14)!, ClockTime(23, 50)!)), "s/2350/20270114")
    }

    func testAReminderIsForAMeetupAheadThatIsNotTurnedOff() {
        let at = Meet.at(Day(2027, 1, 11)!, ClockTime(9, 30)!)
        func item(_ id: String, _ t: Int64) -> MeetReminders.Item {
            .init(id: id, at: t, place: "Goodaura", group: "Gudauri 2027", time: "09:30", open: "meet/158744075b/0930/20270111")
        }
        let items = [item("a", at), item("b", at), item("c", at - 3_600_000)]
        let min: Int64 = 60_000
        // at 09:10: a quarter of an hour before 09:30 is 09:15, still ahead; the one an hour earlier is not
        XCTAssertEqual(MeetReminders.due(items, off: ["b"], now: at - 20 * min).map(\.id), ["a"])
        // at 09:16 it is too late to remind of 09:30
        XCTAssertEqual(MeetReminders.due(items, off: [], now: at - 14 * min).map(\.id), [])
    }

    func testTheReliefReadsTheSitesFile() {
        let r = Self.relief
        XCTAssertEqual(r.nx, 370); XCTAssertEqual(r.ny, 391)
        XCTAssertNotNil(r.hillJPEG)
        XCTAssertFalse(r.c50.isEmpty); XCTAssertFalse(r.c250.isEmpty); XCTAssertFalse(r.c500.isEmpty)
        XCTAssertEqual(r.roads.count, 5); XCTAssertFalse(r.roads[0].isEmpty)
        XCTAssertFalse(r.village.isEmpty); XCTAssertFalse(r.water.isEmpty); XCTAssertFalse(r.rivers.isEmpty)
        XCTAssertTrue(r.places.contains { $0.name == "Kobi" && $0.x == 1343 && $0.y == -5547 })
        XCTAssertTrue(r.peaks.contains { $0.name == "Sadzele" && $0.ele == 3307 })
        // every line is whole pairs
        XCTAssertTrue((r.c50 + r.c100 + r.c250 + r.village + r.rivers + r.water + r.roads.flatMap { $0 }).allSatisfy { $0.count % 2 == 0 })
        // the projection is terrain.json's own
        let t = try! JSONSerialization.jsonObject(with: Data(contentsOf: Self.data.appendingPathComponent("terrain.json"))) as! [String: Any]
        let proj = t["proj"] as! [String: Double]
        XCTAssertEqual(proj["lat0"], Geo.lat0); XCTAssertEqual(proj["lon0"], Geo.lon0)
    }
}
