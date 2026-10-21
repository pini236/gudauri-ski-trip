import XCTest
@testable import GudiDomain

/// The ski school's lessons on the slope, the same cases as Android's SchoolTest: the drills, the coach and the stars.
final class SchoolTests: XCTestCase {
    private final class Ear: SchoolOut {
        var pops: [String] = []
        var coach: [(String, Int)] = []
        var done: SchoolRun.Result?
        func pop(_ say: Say) { pops.append(say.key) }
        func coach(_ say: Say, tone: Int) { coach.append((say.key, tone)) }
        func finished(_ r: SchoolRun.Result) { done = r }
        func lastCoach() -> (String, Int)? { coach.last }
    }
    private let dt: Float = 1 / 60
    private func go(_ r: SchoolRun, _ seconds: Float, each: (SchoolRun) -> Void = { _ in }, until: (SchoolRun) -> Bool = { _ in false }) {
        var t: Float = 0
        while t < seconds && r.running && !until(r) { each(r); r.update(dt); t += dt }
    }
    private func k(_ s: String) -> String { "game.school." + s }

    func testSevenLessonsInTheSiteOrderWithThreeGoalsEach() {
        XCTAssertEqual(LESSONS.count, 7)
        XCTAssertEqual(LESSONS.map(\.mode), [.wedge, .skis, .steer, .rhythm, .steer, .lean, .steer])
        XCTAssertTrue(LESSONS.allSatisfy { $0.goals.count == 3 })
        XCTAssertEqual(LESSONS[1].gates.count, 6); XCTAssertEqual(LESSONS[6].gates.count, 7)
    }

    func testInTheWedgeABrakeBeforeTheLineIsAStopAndRunningPastItIsNot() throws {
        let ear = Ear(); let r = SchoolRun(LESSONS[0], out: ear)
        // the finger low on the screen: a wide wedge, and the skier stops long before the first line (35 m)
        r.finger = SchoolRun.Finger(r.w / 2, r.h * 0.82)
        go(r, 4)
        XCTAssertLessThan(r.s.v, 0.2, "stopped")
        XCTAssertTrue(r.stops.isEmpty, "no stop counted far from the line")
        // no wedge down to the line, and the wide one when it is about as far as the stop takes
        go(r, 40, each: { r in
            r.finger!.y = SchoolRun.stopLines[0] - r.s.y < r.s.v * r.s.v / 5 + 1 ? r.h * 0.82 : r.h * 0.3
        }, until: { !$0.stops.isEmpty })
        XCTAssertEqual(r.stops.count, 1)
        let d = try XCTUnwrap(r.stops[0])
        XCTAssertTrue((0...6).contains(d), "stopped before the line, \(d) m")
        XCTAssertTrue(ear.pops.contains(k("pop_stop_perfect")) || ear.pops.contains(k("pop_stopped")))
        // straight on with no wedge: the second line goes by
        r.finger = SchoolRun.Finger(r.w / 2, r.h * 0.3)
        go(r, 30) { r in if r.stops.count >= 2 { r.finger = nil } }
        XCTAssertNil(r.stops[1])
        XCTAssertTrue(ear.pops.contains(k("pop_line_passed")))
    }

    func testTheOuterSkiTurnsAndTheWrongOneIsCounted() throws {
        let ear = Ear(); let r = SchoolRun(LESSONS[1], out: ear)
        go(r, 0.5)
        // the first gate is on the left (x -5): the right ski, the outer one, turns left
        r.ski(right: false, down: true); r.ski(right: false, down: false)
        XCTAssertEqual(r.wrong, 1)
        XCTAssertEqual(ear.lastCoach()?.0, k("coach_wrong_right")); XCTAssertEqual(ear.lastCoach()?.1, SchoolRun.bad)
        r.ski(right: true, down: true)
        XCTAssertEqual(ear.lastCoach()?.0, k("coach_correct_left")); XCTAssertEqual(ear.lastCoach()?.1, SchoolRun.good)
        let x0 = r.s.x; go(r, 2); XCTAssertLessThan(r.s.x, x0 - 0.5, "turned left")
        r.ski(right: true, down: false)
        // steer for each gate with the right ski and pass all six
        go(r, 200) { r in
            if let g = r.gates.first(where: { $0.done == nil }) {
                let left = g.x < r.s.x
                if r.skiR != left { r.ski(right: true, down: left); r.ski(right: false, down: !left) }
            }
        }
        let res = try XCTUnwrap(ear.done)
        XCTAssertEqual(r.passed, 6)
        XCTAssertTrue(res.goals[0] && res.goals[1]); XCTAssertEqual(res.line.key, k("res_skis"))
    }

    func testATapOnTheBeatIsPerfectAndOffItIsEarlyOrLate() {
        let ear = Ear(); let r = SchoolRun(LESSONS[3], out: ear)
        let b = LESSONS[3].beat
        go(r, 0.6 + b - 0.01) // just before the beat at .6 + one beat
        r.tap(); XCTAssertEqual(r.perfect, 1)
        go(r, b * 0.3); r.tap() // a third of a beat late
        XCTAssertEqual(ear.lastCoach()?.0, k("beat_late")); XCTAssertEqual(ear.lastCoach()?.1, SchoolRun.bad)
        XCTAssertEqual(r.streak, 0)
        go(r, b * 0.45); r.tap() // the next beat comes about a quarter beat later: early
        XCTAssertEqual(ear.lastCoach()?.0, k("beat_early")); XCTAssertEqual(ear.lastCoach()?.1, SchoolRun.bad)
        // ten taps on the beat: the first goal
        var next = 0.6 + b * (floor((r.t - 0.6) / b) + 1)
        for _ in 0..<10 { go(r, next - r.t); r.tap(); next += b }
        XCTAssertGreaterThanOrEqual(r.goodN, 10, "\(r.goodN)"); XCTAssertGreaterThanOrEqual(r.best, 8)
    }

    func testStraightDownIsCaughtInTheTrapAndTurningAcrossSlowsDown() {
        let ear = Ear(); let r = SchoolRun(LESSONS[2], out: ear)
        go(r, 25) // the demo, then no finger: straight on
        XCTAssertFalse(r.traps.isEmpty); XCTAssertEqual(r.traps.first, false, "caught")
        XCTAssertTrue(ear.coach.map(\.0).contains(k("coach_straight")))
        // a second skier who turns across the slope before each trap
        let ear2 = Ear(); let q = SchoolRun(LESSONS[2], out: ear2)
        var side: Float = 1, across = false
        go(q, 120) { q in
            if q.t < q.lv.demo { return }
            if !across && q.kmh > 15 { across = true }
            if across && q.kmh < 9 { across = false; side = -side }
            let a = across ? side * 1.6 : side * 0.5
            q.finger = SchoolRun.Finger(q.sx(q.s.x) + sin(a) * q.w * 0.4, q.sy(q.s.y) - cos(a) * q.w * 0.4)
        }
        XCTAssertTrue(q.traps.contains(true), "passed at least one trap: \(q.traps)")
        XCTAssertTrue(ear2.coach.contains { $0.0 == k("coach_across") && $0.1 == SchoolRun.good })
    }

    func testSlowEdgeChangesDrawCleanArcsAndASnapSkids() {
        let ear = Ear(); let r = SchoolRun(LESSONS[5], out: ear)
        go(r, 40) { r in let s = sin(r.t * 1.4); r.finger = SchoolRun.Finger(r.w / 2 + s * r.w * 0.3, r.h / 2) }
        XCTAssertGreaterThanOrEqual(r.arcs, 3, "arcs \(r.arcs)")
        XCTAssertTrue(ear.pops.contains(k("pop_clean_arc")))
        let snap = SchoolRun(LESSONS[5], out: Ear())
        go(snap, 6) { r in r.finger = SchoolRun.Finger(Int(r.t * 4) % 2 == 0 ? 0 : r.w, r.h / 2) }
        XCTAssertGreaterThan(snap.skidT, 0, "skidding \(snap.skidT)")
    }

    func testTheEndCountsTheStarsAndSaysTheLine() throws {
        let ear = Ear(); let r = SchoolRun(LESSONS[0], out: ear)
        r.finish()
        let res = try XCTUnwrap(ear.done)
        XCTAssertEqual(res.stars, 0)
        XCTAssertEqual(res.line.key, k("res_wedge"))
        XCTAssertEqual(res.line.args["avg"], .text("-")) // no stop: no average distance
        r.finish(); XCTAssertTrue(res === ear.done) // once
        XCTAssertFalse(r.running)
    }

    func testEveryWordTheSchoolSaysIsInTheStrings() throws {
        // every key the lesson can say, with the same placeholders, in i18n/strings.json
        let src = try String(contentsOf: Repo.root.appendingPathComponent("app/ios/Packages/GudiCore/Sources/GudiDomain/Games/School.swift"), encoding: .utf8)
        let json = try JSONSerialization.jsonObject(with: Data(contentsOf: Repo.root.appendingPathComponent("i18n/strings.json"))) as! [String: Any]
        let strings = json["strings"] as! [String: Any]
        let re = try NSRegularExpression(pattern: #"say\("([a-z0-9_]+)""#)
        let ns = src as NSString
        let keys = Set(re.matches(in: src, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range(at: 1)) })
        XCTAssertGreaterThan(keys.count, 40)
        for key in keys { XCTAssertNotNil(strings["game.school." + key], key) }
        for l in LESSONS { for key in [l.title, l.rule, l.ctrl, l.drill, l.tip] + l.goals { XCTAssertNotNil(strings[key], key) } }
    }
}
