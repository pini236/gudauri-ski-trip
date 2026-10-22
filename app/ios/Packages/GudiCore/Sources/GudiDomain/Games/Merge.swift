import Foundation

/// "Merging snowballs" (decision 17; site/games/merge, from design/games/merge): 2048 on snow. Twelve steps, from a flake
/// to the king of Kazbek; a swipe slides every tile, two of a kind become the next. The rules are the site's (and
/// Android's game/Merge.kt), so a board plays the same: merges are scored 2^(level+1), a new tile is a flake (90%) or a
/// small ball, three undos a game, and a whole snowman (level 7) is "won" (the game goes on).
///
/// The board is `size` rows of `size`, column 0 on the left of the screen, and `Dir` is as on the screen.
public final class Merge: @unchecked Sendable {
    public struct Tile: Equatable, Sendable {
        public let id: Int
        public let lv: Int
        public var r: Int
        public var c: Int
        public init(id: Int, lv: Int, r: Int, c: Int) { self.id = id; self.lv = lv; self.r = r; self.c = c }
    }

    public struct Merged: Sendable {
        public let tile: Tile
        public let from: [Tile]
    }

    /// What a move did: tiles that slid (to their new cell), merges (the new tile and the two it came from), the new tile.
    public struct Moved: Sendable {
        public let slid: [Tile]
        public let merges: [Merged]
        public let gained: Int
        public let spawned: Tile?
        public let newTop: Int?
    }

    public enum Dir: Sendable { case left, right, up, down }

    public static let size = 4
    public static let levels = 12
    public static let undosPerGame = 3
    /// A whole snowman: "won" (the game goes on, to the hat, the scarf, the giant and the king).
    public static let won = 7

    public private(set) var grid: [[Tile?]] = Merge.empty()
    public private(set) var score = 0
    public private(set) var maxLv = 0
    public private(set) var undos = Merge.undosPerGame
    private var nid = 1
    private var hist: [(grid: [[Tile?]], score: Int, maxLv: Int)] = []
    private let rnd: GameRandom

    public init(_ rnd: GameRandom = GameRandom()) { self.rnd = rnd }

    private static func empty() -> [[Tile?]] { Array(repeating: Array(repeating: nil, count: size), count: size) }

    public var tiles: [Tile] { grid.flatMap { $0.compactMap { $0 } } }
    public var canUndo: Bool { undos > 0 && !hist.isEmpty }

    public func newGame() {
        grid = Merge.empty(); score = 0; maxLv = 0; undos = Merge.undosPerGame; hist.removeAll()
        _ = spawn(); _ = spawn()
    }

    private func emptyCells() -> [(Int, Int)] {
        var e: [(Int, Int)] = []
        for r in 0..<Merge.size { for c in 0..<Merge.size where grid[r][c] == nil { e.append((r, c)) } }
        return e
    }

    private func spawn() -> Tile? {
        let e = emptyCells()
        if e.isEmpty { return nil }
        let (r, c) = e[rnd.int(e.count)]
        let t = Tile(id: nid, lv: rnd.float() < 0.9 ? 0 : 1, r: r, c: c)
        nid += 1
        grid[r][c] = t
        return t
    }

    /// The cell of line `i` at place `j`, where j = 0 is the side the tiles slide toward.
    private func cellOf(_ dir: Dir, _ i: Int, _ j: Int) -> (Int, Int) {
        let n = Merge.size
        switch dir {
        case .left: return (i, j)
        case .right: return (i, n - 1 - j)
        case .up: return (j, i)
        case .down: return (n - 1 - j, i)
        }
    }

    /// A swipe; nil when nothing could move (the board only nudges).
    public func move(_ dir: Dir) -> Moved? {
        let before = (grid: grid, score: score, maxLv: maxLv)
        var moved = false
        var merges: [Merged] = []
        var slid: [Tile] = []
        for i in 0..<Merge.size {
            let items: [Tile] = (0..<Merge.size).compactMap { j in let (r, c) = cellOf(dir, i, j); return grid[r][c] }
            var out: [[Tile]] = []
            var k = 0
            while k < items.count {
                let a = items[k]
                if k + 1 < items.count, items[k + 1].lv == a.lv, a.lv < Merge.levels - 1 { out.append([a, items[k + 1]]); k += 2 } else { out.append([a]); k += 1 }
            }
            for j in 0..<Merge.size { let (r, c) = cellOf(dir, i, j); grid[r][c] = nil }
            for (j, o) in out.enumerated() {
                let (r, c) = cellOf(dir, i, j)
                if o.count == 2 {
                    moved = true
                    let t = Tile(id: nid, lv: o[0].lv + 1, r: r, c: c); nid += 1
                    grid[r][c] = t; merges.append(Merged(tile: t, from: o))
                } else {
                    var a = o[0]
                    if a.r != r || a.c != c { moved = true; a.r = r; a.c = c; slid.append(a) }
                    grid[r][c] = a
                }
            }
        }
        if !moved { return nil }
        hist.append(before); if hist.count > Merge.undosPerGame { hist.removeFirst() }
        var gained = 0
        var top: Int?
        for m in merges { gained += 1 << (m.tile.lv + 1); if m.tile.lv > maxLv { maxLv = m.tile.lv; top = m.tile.lv } }
        score += gained
        return Moved(slid: slid, merges: merges, gained: gained, spawned: spawn(), newTop: top)
    }

    public func canMove() -> Bool {
        if !emptyCells().isEmpty { return true }
        let n = Merge.size
        for r in 0..<n { for c in 0..<n {
            guard let v = grid[r][c]?.lv else { return true }
            if c < n - 1, grid[r][c + 1]?.lv == v { return true }
            if r < n - 1, grid[r + 1][c]?.lv == v { return true }
        } }
        return false
    }

    @discardableResult
    public func undo() -> Bool {
        guard canUndo, let h = hist.popLast() else { return false }
        grid = h.grid; score = h.score; maxLv = h.maxLv; undos -= 1
        return true
    }

    /// The board kept on the phone (the site's "save"), in Android's JSON shape: the game goes on after the app closes.
    public func save() -> String {
        let g = tiles.map { [$0.id, $0.lv, $0.r, $0.c] }
        let o: [String: Any] = ["score": score, "maxLv": maxLv, "undos": undos, "g": g]
        guard let d = try? JSONSerialization.data(withJSONObject: o) else { return "" }
        return String(decoding: d, as: UTF8.self)
    }

    @discardableResult
    public func restore(_ s: String?) -> Bool {
        guard let s, let d = s.data(using: .utf8),
              let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any],
              let a = o["g"] as? [[Int]], let sc = o["score"] as? Int, let m = o["maxLv"] as? Int else { return false }
        var g = Merge.empty()
        for t in a {
            guard t.count == 4, (0..<Merge.size).contains(t[2]), (0..<Merge.size).contains(t[3]), (0..<Merge.levels).contains(t[1]) else { return false }
            g[t[2]][t[3]] = Tile(id: t[0], lv: t[1], r: t[2], c: t[3])
        }
        grid = g; score = sc; maxLv = m; undos = o["undos"] as? Int ?? Merge.undosPerGame; hist.removeAll()
        nid = 1 + (tiles.map(\.id).max() ?? 0)
        return true
    }

    /// For the tests: a board as written, levels by row (-1 empty).
    public func set(_ levels: [[Int]]) {
        var g = Merge.empty()
        for r in 0..<Merge.size { for c in 0..<Merge.size where levels[r][c] >= 0 { g[r][c] = Tile(id: nid, lv: levels[r][c], r: r, c: c); nid += 1 } }
        grid = g
        maxLv = tiles.map(\.lv).max() ?? 0
    }
}
