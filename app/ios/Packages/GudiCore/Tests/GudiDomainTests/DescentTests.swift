import XCTest
@testable import GudiDomain

/// The descent (Games/Descent): the runs laid out exactly as on the site (the numbers below are what the site's own
/// buildRun() makes, run in Node, the same as Android's DescentGameTest), and the skier gets down.
final class DescentTests: XCTestCase {
    private static let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()

    /// The runs straight from the site's game (its page, or the script of its own next to it).
    private static let profiles: [DescentProfile] = {
        let dir = root.appendingPathComponent("site/games/descent")
        let files = ((try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []).filter { $0.hasSuffix(".js") }.sorted()
        let marker = "const PROFILES = "
        for f in ["index.html"] + files {
            guard let text = try? String(contentsOf: dir.appendingPathComponent(f), encoding: .utf8), let r = text.range(of: marker),
                  let end = text.range(of: "];", range: r.upperBound..<text.endIndex) else { continue }
            let json = String(text[r.upperBound..<end.lowerBound]) + "]"
            return (try? DescentProfile.parse(Data(json.utf8))) ?? []
        }
        return []
    }()

    private struct E { let key: String; let len: Float; let top: Int; let bot: Int; let kick: [Int]; let gaps: [Float]; let rocks: [Float]; let cat: Float
        let zones: Int; let coins: Int; let spots: [Float]; let g100: Float; let s100: Float }
    private let site = [
        E(key: "Pirveli", len: 1140, top: 2152, bot: 1993, kick: [120, 295, 470, 645, 820, 915], gaps: [], rocks: [164.172, 429.068], cat: 553.554, zones: 0, coins: 58, spots: [182, 329.4, 504.8, 673.2, 855.6], g100: -16.1767, s100: -0.16687),
        E(key: "Kikilo 1", len: 1150, top: 2766, bot: 2450, kick: [120, 295, 460, 635, 810, 985], gaps: [810.5], rocks: [353.667, 494.076, 783.118, 916.479], cat: 563.34, zones: 2, coins: 38, spots: [148, 325, 509, 679, 863], g100: -36.5767, s100: -0.28081),
        E(key: "Tatra 2", len: 2270, top: 2665, bot: 2170, kick: [120, 295, 470, 565, 740, 915, 1090, 1260, 1435, 1530, 1705, 1880, 2055], gaps: [740.5], rocks: [329.231, 697.928, 1922.292, 2163.348], cat: 1044.768, zones: 1, coins: 109, spots: [344, 602.2, 965.4, 1328.6, 1733.8], g100: -15.2767, s100: -0.16687),
        E(key: "Sadzele 3", len: 1320, top: 3242, bot: 2895, kick: [120, 245, 340, 515, 655, 775, 950, 1125], gaps: [515.5, 950.5], rocks: [173.608, 284.861, 397.695, 563.668, 715.81, 893.173, 1084.089, 1223.604], cat: 700.52, zones: 3, coins: 52, spots: [151, 369.2, 580.4, 805.6, 988.8], g100: -69.1267, s100: -0.54294),
        E(key: "Sadzele 1", len: 2010, top: 3232, bot: 2777, kick: [120, 215, 390, 505, 665, 790, 965, 1085, 1260, 1435, 1570, 1745], gaps: [790.5, 505.5], rocks: [188.281, 340.574, 584.914, 736.147, 849.46, 1123.722, 1232.391, 1354.581, 1481.59, 1717.009, 1827.749], cat: 1131.22, zones: 4, coins: 84, spots: [248, 534.6, 863.2, 1177.8, 1499.4], g100: -52.1767, s100: -0.67379),
    ]

    func testTheSiteRngGivesTheSameNumbers() {
        var r = SiteRng(17)
        XCTAssertEqual(r.next(), 0.6771502960473299, accuracy: 1e-12)
        XCTAssertEqual(r.next(), 0.19265692122280598, accuracy: 1e-12)
        XCTAssertEqual(r.next(), 0.5313839064911008, accuracy: 1e-12)
    }

    func testEveryRunIsLaidOutAsOnTheSite() {
        let profiles = Self.profiles
        XCTAssertEqual(profiles.count, 5)
        for (i, e) in site.enumerated() where i < profiles.count {
            let c = Course(profiles[i], index: i, me: 5)
            XCTAssertEqual(c.key, e.key); XCTAssertEqual(c.len, e.len, accuracy: 0.01); XCTAssertEqual(c.top, e.top); XCTAssertEqual(c.bot, e.bot)
            XCTAssertEqual(c.kickers.map { Int($0.x) }, e.kick, e.key)
            XCTAssertEqual(c.gaps.count, e.gaps.count, e.key)
            for (a, b) in zip(c.gaps.map(\.x0), e.gaps) { XCTAssertEqual(a, b, accuracy: 0.001, e.key) }
            XCTAssertEqual(c.rocks.count, e.rocks.count, e.key)
            for (a, b) in zip(c.rocks.map(\.x), e.rocks) { XCTAssertEqual(a, b, accuracy: 0.01, e.key) }
            XCTAssertEqual(c.cats[0].x, e.cat, accuracy: 0.01, e.key)
            XCTAssertEqual(c.zones.count, e.zones, e.key); XCTAssertEqual(c.coins.count, e.coins, e.key)
            XCTAssertEqual(c.spots.count, e.spots.count, e.key)
            for (a, b) in zip(c.spots.map(\.x), e.spots) { XCTAssertEqual(a, b, accuracy: 0.01, e.key) }
            XCTAssertEqual(c.ground(100), e.g100, accuracy: 0.001, e.key); XCTAssertEqual(c.slope(100), e.s100, accuracy: 0.0001, e.key)
            XCTAssertEqual(c.spots.map(\.coat), [0, 1, 2, 3, 4]) // the other five coats
        }
    }

    private final class Ear: DescentOut {
        var pops: [DescentWord] = []; var done: Bool?
        func pop(_ w: DescentWord) { pops.append(w) }
        func finished(_ ok: Bool) { done = ok }
    }

    func testWithNoInputTheSkierGetsDownEveryRun() {
        for (i, p) in Self.profiles.enumerated() {
            let ear = Ear(); let g = DescentGame(Course(p, index: i, me: 5), me: 5, out: ear, avalancheOn: false, dice: SeededDice(seed: UInt64(i + 1)))
            var t: Float = 0
            while g.state != .end && t < 900 { g.step(1 / 60); t += 1 / 60; XCTAssertTrue(g.x.isFinite && g.y.isFinite, p.key) }
            XCTAssertEqual(ear.done, true, p.key)
            XCTAssertGreaterThan(g.score, 0, p.key); XCTAssertTrue((0...5).contains(g.got), p.key)
        }
    }

    func testAPopAtTheLipIsPerfectAndAFlipIsCounted() throws {
        let profiles = Self.profiles; try XCTSkipIf(profiles.count < 3)
        let ear = Ear(); let c = Course(profiles[2], index: 2, me: 5); let g = DescentGame(c, me: 5, out: ear, avalancheOn: false, dice: SeededDice(seed: 1))
        for _ in 0..<81 { g.step(0.05) } // the count, then off
        XCTAssertEqual(g.state, .run)
        // on the snow just before the first kicker's lip, crouched a moment, then let go on the yellow line
        let k = c.kickers[0].x
        g.x = k - 2; g.y = c.ground(g.x); g.air = false; g.s = 4
        g.press(); g.step(0.1); g.release()
        XCTAssertTrue(ear.pops.contains(.popPerfect)); XCTAssertTrue(g.air); XCTAssertEqual(g.stats.pops, 1)
        // hold in the air: a flip; let go to land it
        g.press(); var n = 0; while g.air && n < 600 { if g.airRot > 6.5 { g.release() }; g.step(1 / 60); n += 1 }
        XCTAssertTrue(g.flips >= 1 || g.stats.falls >= 1)
    }

    func testTheEndCountsTheStarsAndTheBonus() throws {
        let profiles = Self.profiles; try XCTSkipIf(profiles.isEmpty)
        let ear = Ear(); let g = DescentGame(Course(profiles[0], index: 0, me: 5), me: 5, out: ear, avalancheOn: false)
        g.finish(true)
        XCTAssertEqual(ear.done, true); XCTAssertTrue(g.goals[0]); XCTAssertFalse(g.goals[1])
        XCTAssertEqual(g.score, 600) // no crew yet, at t 0: 600 - 3t
        let ghost = g.record(); XCTAssertEqual(DescentGame.Ghost.load(ghost.save())?.t, ghost.t)
    }
}
