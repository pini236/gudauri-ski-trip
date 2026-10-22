import XCTest
@testable import GudiDomain

/// The snowball fight (Games/Snowball), the site's rules, as Android's SnowballTest: throws, the far wall, ducking, the stars.
final class SnowballTests: XCTestCase {
    private final class Ear: SnowballOut {
        var pops: [SnowballWord] = []; var done: SnowballFight.Result?
        func pop(_ w: SnowballWord) { pops.append(w) }
        func finished(_ r: SnowballFight.Result) { done = r }
    }
    private let dt: Float = 1 / 60
    private func go(_ g: SnowballFight, _ seconds: Float) { var t: Float = 0; while t < seconds { g.update(dt); t += dt } }
    /// A fight on `rung` with no wind, every character standing and aiming, and never throwing.
    private func standing(_ rung: Int = 0, _ ear: Ear) -> SnowballFight {
        let g = SnowballFight(rung: rung, me: 5, out: ear, dice: SeededDice(seed: 1))
        g.place(390, 844, 1); g.wind = 0
        for o in g.foes { o.up = 1; o.want = 1; o.phase = SnowballFight.aimPhase; o.timer = 999 }
        return g
    }

    func testTheFoesAreTheOtherCoatsAndMoreUpTheLadder() {
        XCTAssertEqual(Rung.all.indices.map { Rung.foes($0, me: 5).count }, [1, 1, 2, 2, 3])
        for me in 0..<Coats.count { for i in Rung.all.indices { let f = Rung.foes(i, me: me); XCTAssertFalse(f.contains(me)); XCTAssertEqual(f.count, Set(f).count) } }
    }

    func testABallAtTheHeadKnocksTheHatOffAndAtTheBodyIsAHit() {
        let ear = Ear(); let g = standing(0, ear)
        let o = g.foes[0]; let head = g.base(o) + 1.62
        g.camH = 1.75; g.throwAt(o.x, head); go(g, 1.2)
        XCTAssertEqual(g.stats.hits, 1); XCTAssertEqual(g.stats.hats, 1); XCTAssertFalse(o.hat); XCTAssertEqual(o.hp, 2)
        XCTAssertTrue(ear.pops.contains(.popHatOff))
        g.camH = 1.75; g.throwAt(o.x, g.base(o) + 1.17); go(g, 1.2)
        XCTAssertEqual(g.stats.hits, 2); XCTAssertEqual(g.stats.hats, 1)
        XCTAssertTrue(ear.pops.contains(.popHit)); XCTAssertEqual(g.stats.thrown, 2)
    }

    func testALowBallHitsTheirWallAndWearsItDown() {
        let g = standing(0, Ear())
        let before = g.farWallTop(0)
        g.camH = 1.75; g.throwAt(0, 0.3); go(g, 1.2)
        XCTAssertEqual(g.stats.hits, 0)
        XCTAssertLessThan(g.farWallTop(0), before - 0.05)
    }

    func testTheirBallHitsYouOnlyWhenYouAreUp() {
        let ear = Ear(); let g = standing(0, ear)
        g.down(195, 400); go(g, 0.5)
        g.balls.append(SnowballFight.Ball(g.camX, g.camH, 0.6, 0, 0, -5, false)); go(g, 0.3)
        XCTAssertEqual(g.myHp, 2); XCTAssertEqual(g.stats.taken, 1); XCTAssertFalse(g.splats.isEmpty)
        XCTAssertTrue(ear.pops.contains(.popGotHit))
        g.cancel(); go(g, 0.6)
        g.balls.append(SnowballFight.Ball(g.camX, 1.75, 0.6, 0, 0, -5, false)); go(g, 0.3)
        XCTAssertEqual(g.myHp, 2)
        XCTAssertTrue(ear.pops.contains(.popOverYou))
    }

    func testLettingGoBeforeYouAreUpIsJustDucking() {
        let g = standing(0, Ear())
        g.down(195, 300); g.up()
        XCTAssertEqual(g.stats.thrown, 0)
        g.down(195, 300); go(g, 0.5); g.up()
        XCTAssertEqual(g.stats.thrown, 1)
    }

    func testThreeHitsWinWithAllStarsAndThreeTakenLose() throws {
        let ear = Ear(); let g = standing(0, ear)
        let o = g.foes[0]
        for _ in 0..<3 { g.camH = 1.75; g.throwAt(o.x, g.base(o) + 1.62); go(g, 1.2) }
        XCTAssertEqual(o.hp, 0); XCTAssertTrue(ear.pops.contains(.popOut))
        go(g, 2)
        let r = try XCTUnwrap(ear.done)
        XCTAssertTrue(r.won); XCTAssertEqual(r.stars, 3); XCTAssertEqual(r.goals, [true, true, true])
        XCTAssertTrue(ear.pops.contains(.won))

        let ear2 = Ear(); let q = standing(1, ear2)
        q.down(195, 400); go(q, 0.5)
        for _ in 0..<3 { q.balls.append(SnowballFight.Ball(q.camX, q.camH, 0.6, 0, 0, -5, false)); go(q, 0.3) }
        go(q, 2)
        let l = try XCTUnwrap(ear2.done)
        XCTAssertFalse(l.won); XCTAssertEqual(l.stars, 0); XCTAssertEqual(l.taken, 3)
        ear2.done = nil; q.finish(); XCTAssertNil(ear2.done) // once
    }

    func testTheRungGoals() {
        let s = SnowballFight.Stats()
        XCTAssertTrue(Rung.all[0].ok(s)); s.taken = 1; XCTAssertFalse(Rung.all[0].ok(s))
        XCTAssertFalse(Rung.all[1].ok(s)); s.hats = 1; XCTAssertTrue(Rung.all[1].ok(s)); XCTAssertFalse(Rung.all[3].ok(s))
        s.thrown = 4; s.hits = 2; XCTAssertTrue(Rung.all[2].ok(s)); s.hits = 1; XCTAssertFalse(Rung.all[2].ok(s))
        XCTAssertEqual(Rung.all.map(\.windMax), [1, 2, 2, 4, 5])
    }
}
