import XCTest
@testable import GudiDomain

/// The site's rules (site/games/merge), the same cases as Android's MergeTest: one merge per tile per move,
/// 2^(level+1) points, three undos, a full board ends it.
final class MergeTests: XCTestCase {
    private func levels(_ m: Merge) -> [[Int]] { m.grid.map { $0.map { $0?.lv ?? -1 } } }

    func testTwoOfAKindBecomeTheNextOnceAMove() throws {
        let m = Merge(GameRandom(seed: 1))
        m.set([[0, 0, 0, 0], [1, -1, 1, -1], [2, 2, -1, -1], [-1, -1, -1, 11]])
        let mv = try XCTUnwrap(m.move(.left))
        let l = levels(m)
        // four flakes make two small balls, not one ball; across a gap they still meet; the king of Kazbek stays the king
        XCTAssertEqual(Array(l[0].prefix(2)), [1, 1])
        XCTAssertEqual(l[1][0], 2); XCTAssertEqual(l[2][0], 3); XCTAssertEqual(l[3][0], 11)
        XCTAssertEqual(mv.merges.count, 4)
        XCTAssertEqual(mv.gained, 4 + 4 + 8 + 16)
        XCTAssertEqual(m.score, mv.gained)
        XCTAssertNotNil(mv.spawned)
    }

    func testNothingMovesNothingHappens() {
        let m = Merge(GameRandom(seed: 2))
        m.set([[0, 1, -1, -1], [-1, -1, -1, -1], [-1, -1, -1, -1], [-1, -1, -1, -1]])
        XCTAssertNil(m.move(.left))
        XCTAssertNil(m.move(.up))
        XCTAssertNotNil(m.move(.right))
    }

    func testThreeUndosAGame() {
        let m = Merge(GameRandom(seed: 3))
        m.newGame()
        XCTAssertFalse(m.canUndo)
        var moves = 0
        for d in [Merge.Dir.left, .right, .up, .down, .left, .right, .up, .down] where m.move(d) != nil { moves += 1 }
        XCTAssertGreaterThanOrEqual(moves, 4)
        for _ in 0..<3 { XCTAssertTrue(m.undo()) }
        XCTAssertFalse(m.undo(), "three undos a game")
    }

    func testAFullBoardWithNoPairsIsTheEnd() {
        let m = Merge(GameRandom(seed: 4))
        m.set([[0, 1, 0, 1], [1, 0, 1, 0], [0, 1, 0, 1], [1, 0, 1, 0]])
        XCTAssertFalse(m.canMove())
        m.set([[0, 1, 0, 1], [1, 0, 1, 0], [0, 1, 0, 1], [1, 0, 1, 1]])
        XCTAssertTrue(m.canMove())
    }

    func testTheBoardIsKeptAndComesBack() {
        let m = Merge(GameRandom(seed: 5))
        m.newGame(); _ = m.move(.left); _ = m.move(.up)
        let b = Merge(GameRandom(seed: 6))
        XCTAssertTrue(b.restore(m.save()))
        XCTAssertEqual(levels(m), levels(b)); XCTAssertEqual(m.score, b.score); XCTAssertEqual(m.undos, b.undos)
        XCTAssertFalse(b.restore("{oops"))
        // Android's saved shape reads the same
        XCTAssertTrue(b.restore(#"{"score":12,"maxLv":2,"undos":1,"g":[[7,2,0,3],[9,0,3,0]]}"#))
        XCTAssertEqual(b.score, 12); XCTAssertEqual(b.undos, 1); XCTAssertEqual(b.grid[0][3]?.lv, 2); XCTAssertEqual(b.grid[3][0]?.id, 9)
    }

    func testTheSitesNumbers() throws {
        let js = try String(contentsOf: Repo.root.appendingPathComponent("site/games/merge/game.js"), encoding: .utf8)
        XCTAssertTrue(js.contains("undos=3"), "three undos, as on the site")
        XCTAssertTrue(js.contains("2**(m.t.lv+1)"), "2^(level+1) points a merge")
        XCTAssertTrue(js.contains(".9?0:1"), "a flake nine times in ten")
        XCTAssertEqual(Merge.levels, 12); XCTAssertEqual(Merge.won, 7); XCTAssertEqual(Merge.undosPerGame, 3)
    }
}
