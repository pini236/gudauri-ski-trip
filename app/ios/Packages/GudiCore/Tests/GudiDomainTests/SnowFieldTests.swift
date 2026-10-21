import XCTest
@testable import GudiDomain

/// Fresh snow's surface, the same cases as Android's SnowFieldTest: presses, the rim, the snowcat and new snow.
final class SnowFieldTests: XCTestCase {
    private func field() -> SnowField { SnowField(gw: 80, gh: 120, rnd: GameRandom(seed: 3)) }

    func testAFingerPressesTheSnowDownAndALittleUpAroundIt() {
        let f = field()
        let c = 60 * 80 + 40
        let moved = f.press(40, 60, 6, 6, 0, 1.3)
        XCTAssertGreaterThan(moved, 0)
        XCTAssertLessThan(f.h[c], f.base[c] - 1, "the middle is lower")
        XCTAssertNotEqual(f.touched[c], 0)
        // the rim, a little past the stamp: powder rises there
        let rim = 60 * 80 + 40 + 8
        XCTAssertGreaterThan(f.h[rim], f.base[rim], "the rim rises")
        XCTAssertGreaterThan(f.tracks(), 0)
    }

    func testTheFrozenCrustCracksAndTheSnowcatSmoothsItAgain() {
        let f = field(); f.kind = .crust
        f.press(40, 60, 13, 13, 0, 1.7)
        XCTAssertTrue(f.crack.contains(true))
        f.groom(40, 60, 0) // the blade across the middle
        let c = 60 * 80 + 40
        XCTAssertLessThan(abs(f.h[c] - (f.base[c] - 0.1)), 0.05, "flattened near the base, with corduroy")
        XCTAssertTrue(f.touched[c] == 0 && !f.crack[c])
    }

    func testNewSnowFillsEveryTrackBackIn() {
        let f = field()
        f.press(40, 60, 6, 6, 0, 1.3)
        for _ in 0..<400 { f.fill(0.05) }
        XCTAssertEqual(f.tracks(), 0)
        XCTAssertTrue(f.h.indices.allSatisfy { f.h[$0] == f.base[$0] })
    }

    func testWetTracksStayWetWhenTheKindChanges() {
        let f = field(); f.kind = .wet
        f.press(40, 60, 6, 6, 0, 1.3)
        f.kind = .powder
        XCTAssertEqual(f.touched[60 * 80 + 40], 2, "a track keeps the snow it was made in (X-5)")
    }

    func testABootLeavesASoleNotABowl() {
        XCTAssertEqual(SnowField.boot(0.9, 0.9), 0) // beside the heel
        XCTAssertGreaterThan(SnowField.boot(0, -0.38), 0) // the toe
        XCTAssertGreaterThan(SnowField.boot(0, 0.55), 0) // the heel
    }

    func testTheLightIsBrightOnFreshSnow() {
        let f = field()
        var px = [UInt32](repeating: 0, count: 80 * 120)
        f.render(&px)
        let c = px[60 * 80 + 40]
        XCTAssertTrue((c >> 16 & 0xFF) > 195 && (c & 0xFF) > 200 && (c & 0xFF) >= (c >> 16 & 0xFF))
    }
}
