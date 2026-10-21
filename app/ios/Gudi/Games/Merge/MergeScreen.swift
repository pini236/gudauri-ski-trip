import SwiftUI
import GudiDomain

// one look: a cold blue evening on the slope, snow tiles on packed snow (the site's palette), by day and by night
private let bgTop = Color(rgb: 0x2C4A6C), bg = Color(rgb: 0x16263A), boardC = Color(rgb: 0x25405E), cellC = Color(rgb: 0x2E4E70)
private let inkC = Color(rgb: 0x0D1522), paperC = Color(rgb: 0xF4F8FB), accentC = Color(rgb: 0xF4B942), softC = Color(rgb: 0xA9C1D8)
private let lineC = Color(rgb: 0xC7D7E6)

private func mergeName(_ lv: Int) -> String { Strings.shared.text("game.merge.name_\(lv + 1)") }

/// A tile on the screen: where it is, and how it came (new pops in, merged bumps, gone slides under and goes).
private struct TileView: Identifiable, Equatable {
    enum Kind { case plain, new, merged, gone }
    let id: Int, lv: Int
    var r: Int, c: Int
    var kind: Kind
}

private struct Puff { var x, y, vx, vy: CGFloat; let r: CGFloat; var life: CGFloat; let gold: Bool }
private struct Plus: Identifiable { let id: Int, r: Int, c: Int, n: Int; let born: Date }

/// The puffs of snow, stepped by the frame clock (not observed: they move every frame).
private final class Puffs {
    var list: [Puff] = []
    var last: Date?
    func step(_ now: Date, _ cs: CGFloat) {
        let dt = CGFloat(min(0.05, last.map { now.timeIntervalSince($0) } ?? 0.016)); last = now
        for i in list.indices {
            list[i].vy += cs * 2.2 * dt; list[i].vx *= exp(-3 * dt)
            list[i].x += list[i].vx * dt; list[i].y += list[i].vy * dt; list[i].life -= dt
        }
        list.removeAll { $0.life <= 0 }
        if list.isEmpty { last = nil }
    }
}

@MainActor
@Observable
private final class MergeModel {
    let game = Merge()
    var tiles: [TileView] = []
    var score = 0
    var best = 0
    var maxLv = 0
    var undos = Merge.undosPerGame
    var canUndo = false
    var got = UserDefaults.standard.integer(forKey: "merge.got")
    var over: String? // "won" or "over"
    var wonShown = false
    var toast: Int?
    var pluses: [Plus] = []
    var bump = false
    var nudge: CGSize = .zero
    let puffs = Puffs()
    @ObservationIgnored var cs: CGFloat = 60
    @ObservationIgnored var gap: CGFloat = 8
    @ObservationIgnored var still = false
    let synth = Synth()

    init() {
        best = Bests.get("merge")
        if !game.restore(UserDefaults.standard.string(forKey: "merge.save")) { game.newGame() }
        wonShown = game.maxLv >= Merge.won
        sync()
    }

    func save() { UserDefaults.standard.set(game.save(), forKey: "merge.save") }
    func sync() {
        tiles = game.tiles.map { TileView(id: $0.id, lv: $0.lv, r: $0.r, c: $0.c, kind: .plain) }
        score = game.score; maxLv = game.maxLv; undos = game.undos; canUndo = game.canUndo
    }
    func newGame() {
        game.newGame(); wonShown = false; over = nil
        tiles = game.tiles.map { TileView(id: $0.id, lv: $0.lv, r: $0.r, c: $0.c, kind: .new) }
        score = 0; maxLv = 0; undos = game.undos; canUndo = false
        save()
    }
    func undo() { if game.undo() { sync(); save() } }

    func puff(_ r: Int, _ c: Int, _ lv: Int) {
        if still { return }
        let cx = gap + CGFloat(c) * (cs + gap) + cs / 2, cy = gap + CGFloat(r) * (cs + gap) + cs / 2
        for _ in 0..<(10 + lv * 3) {
            let a = CGFloat.random(in: 0..<6.283), v = (0.6 + CGFloat.random(in: 0..<1)) * cs * (0.9 + CGFloat(lv) * 0.06)
            puffs.list.append(Puff(x: cx, y: cy, vx: cos(a) * v, vy: sin(a) * v - cs * 0.4, r: 2 + CGFloat.random(in: 0..<1) * cs * 0.05,
                                   life: 0.5 + CGFloat.random(in: 0..<0.3), gold: lv >= 10 && Bool.random() && Bool.random()))
        }
    }

    func move(_ d: Merge.Dir) {
        if over != nil { return }
        guard let mv = game.move(d) else {
            if still { return }
            let k: CGFloat = 6
            withAnimation(.easeOut(duration: 0.09)) {
                nudge = switch d { case .left: CGSize(width: -k, height: 0); case .right: CGSize(width: k, height: 0); case .up: CGSize(width: 0, height: -k); case .down: CGSize(width: 0, height: k) }
            } completion: { withAnimation(.easeIn(duration: 0.09)) { self.nudge = .zero } }
            return
        }
        synth.swish()
        // 1. everything slides; the two halves of a merge slide into the same cell and go
        let slide = still ? 0.0 : 0.11
        var gone: [Int: (Int, Int)] = [:]
        for m in mv.merges { for f in m.from { gone[f.id] = (m.tile.r, m.tile.c) } }
        let slidTo = Dictionary(uniqueKeysWithValues: mv.slid.map { ($0.id, ($0.r, $0.c)) })
        withAnimation(still ? nil : .easeInOut(duration: slide)) {
            for i in tiles.indices {
                if let g = gone[tiles[i].id] { tiles[i].r = g.0; tiles[i].c = g.1; tiles[i].kind = .gone }
                else if let s = slidTo[tiles[i].id] { tiles[i].r = s.0; tiles[i].c = s.1; tiles[i].kind = .plain }
                else { tiles[i].kind = .plain }
            }
        }
        canUndo = game.canUndo; undos = game.undos
        // 2. when they arrive: the bigger ball pops in with a puff, the score counts up, a new flake appears
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(slide))
            tiles.removeAll { gone[$0.id] != nil }
            for m in mv.merges {
                tiles.append(TileView(id: m.tile.id, lv: m.tile.lv, r: m.tile.r, c: m.tile.c, kind: .merged))
                pluses.append(Plus(id: m.tile.id, r: m.tile.r, c: m.tile.c, n: 1 << (m.tile.lv + 1), born: .now))
                puff(m.tile.r, m.tile.c, m.tile.lv)
            }
            if let s = mv.spawned { tiles.append(TileView(id: s.id, lv: s.lv, r: s.r, c: s.c, kind: .new)) }
            if !mv.merges.isEmpty {
                let top = mv.merges.map(\.tile.lv).max() ?? 0
                synth.crunch(top, volume: 0.18 + 0.05 * Float(mv.merges.count))
                if mv.merges.count > 1 { GameHaptics.click(0.6) } else { GameHaptics.tick(0.5) }
                score = game.score
                if !still { bump = true; withAnimation(.easeOut(duration: 0.25)) { bump = false } }
                if score > best { best = score; Bests.offer("merge", score) }
                if game.maxLv > maxLv { maxLv = game.maxLv }
                if top > got {
                    got = top; UserDefaults.standard.set(top, forKey: "merge.got")
                    if top >= 3 {
                        withAnimation { toast = top }
                        Task { @MainActor in try? await Task.sleep(for: .seconds(1.7)); if toast == top { withAnimation { toast = nil } } }
                    }
                }
                Task { @MainActor in try? await Task.sleep(for: .seconds(0.85)); pluses.removeAll { Date.now.timeIntervalSince($0.born) > 0.8 } }
            }
            save()
            if maxLv >= Merge.won && !wonShown { wonShown = true; try? await Task.sleep(for: .seconds(0.5)); withAnimation { over = "won" } }
            else if !game.canMove() { try? await Task.sleep(for: .seconds(0.4)); withAnimation { over = "over" } }
        }
    }

    /// "Continue" after the snowman: when that merge filled the board, the end (X-5, the site's noRoom).
    func goOn() {
        over = nil
        if !game.canMove() { Task { @MainActor in try? await Task.sleep(for: .seconds(0.4)); withAnimation { over = "over" } } }
    }
}

/// "Merging snowballs" on the phone (13.6), the site's game (site/games/merge) and Android's MergeScreen.kt: a swipe
/// anywhere slides the board, two of a kind become the next, a puff of snow and a crunch for every merge, the next goal
/// and the ladder of names, three undos, and the board kept on the phone. The high score is the phone's (Bests) and goes
/// to the group's table.
struct MergeScreen: View {
    @State private var m = MergeModel()
    @State private var fired = false
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still

    var body: some View {
        ZStack {
            LinearGradient(colors: [bgTop, bg], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 12) {
                HStack { Spacer(); GameBack(text: S.gameMergeBackToGames, color: lineC) }
                header
                goal
                board
                buttons
                ladder
                Text(S.gameMergeHint).font(ski.type.text(12)).foregroundStyle(softC).multilineTextAlignment(.center)
            }
            .frame(maxWidth: 440)
            .padding(.horizontal, 16).padding(.vertical, 6)
            if let lv = m.toast { toastView(lv).transition(.scale.combined(with: .opacity)) }
            if let kind = m.over { endView(kind).transition(.opacity) }
        }
        .contentShape(Rectangle())
        // the swipe fires while the finger is still moving, anywhere on the screen
        .gesture(DragGesture(minimumDistance: 0).onChanged { v in
            if fired { return }
            let dx = v.translation.width, dy = v.translation.height
            guard max(abs(dx), abs(dy)) >= 18 else { return }
            fired = true
            m.move(abs(dx) > abs(dy) ? (dx < 0 ? .left : .right) : (dy < 0 ? .up : .down))
        }.onEnded { _ in fired = false })
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { m.still = still; m.synth.start() }
        .onDisappear { m.synth.stop() }
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            Text(S.gameMergeTitle).font(ski.type.display(40)).foregroundStyle(paperC).lineLimit(1).minimumScaleFactor(0.6)
            Spacer(minLength: 6)
            HStack(spacing: 6) {
                scoreBox(S.gameMergeScore, m.score).scaleEffect(m.bump ? 1.25 : 1)
                scoreBox(S.gameMergeBest, m.best)
            }
        }
    }

    private func scoreBox(_ label: String, _ n: Int) -> some View {
        VStack(spacing: 0) {
            Text(label).font(ski.type.text(11)).foregroundStyle(softC)
            Text("\(n)").font(ski.type.display(28)).foregroundStyle(paperC).monospacedDigit()
        }
        .padding(.horizontal, 8).padding(.vertical, 4).frame(minWidth: 54).background(boardC)
        .accessibilityElement(children: .combine)
    }

    private var goal: some View {
        HStack(spacing: 10) {
            Text(m.maxLv >= Merge.levels - 1 ? S.gameMergeGoalTop : S.gameMergeGoalNext(name: mergeName(min(Merge.levels - 1, m.maxLv + 1))))
                .font(ski.type.text(13)).foregroundStyle(lineC)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Rectangle().fill(boardC)
                    Rectangle().fill(accentC).frame(width: g.size.width * CGFloat(m.maxLv) / CGFloat(Merge.levels - 1))
                }
            }
            .frame(height: 8)
        }
    }

    private var board: some View {
        GeometryReader { g in
            let side = min(g.size.width, g.size.height)
            let gap = side * 0.025, cs = (side - 5 * gap) / 4
            let _ = { m.cs = cs; m.gap = gap }()
            ZStack(alignment: .topLeading) {
                boardC
                ForEach(0..<16, id: \.self) { i in
                    cellC.frame(width: cs, height: cs).offset(x: gap + CGFloat(i % 4) * (cs + gap), y: gap + CGFloat(i / 4) * (cs + gap))
                }
                ForEach(m.tiles.sorted { ($0.kind == .gone ? 0 : 1) < ($1.kind == .gone ? 0 : 1) }) { t in
                    TileCell(lv: t.lv, kind: t.kind, still: still)
                        .frame(width: cs, height: cs)
                        .offset(x: gap + CGFloat(t.c) * (cs + gap), y: gap + CGFloat(t.r) * (cs + gap))
                }
                // the puffs and the points over the board
                TimelineView(.animation(paused: m.puffs.list.isEmpty && m.pluses.isEmpty)) { tl in
                    Canvas { c, _ in
                        m.puffs.step(tl.date, cs)
                        for p in m.puffs.list {
                            c.opacity = min(1, max(0, p.life * 2.5))
                            c.fill(Path(ellipseIn: CGRect(x: p.x - p.r, y: p.y - p.r, width: 2 * p.r, height: 2 * p.r)), with: .color(p.gold ? accentC : .white))
                        }
                        c.opacity = 1
                        for p in m.pluses {
                            let a = min(1, tl.date.timeIntervalSince(p.born) / 0.8)
                            c.opacity = 1 - a
                            c.draw(Text("+\(p.n)").font(ski.type.display(cs * 0.32)).foregroundStyle(accentC),
                                   at: CGPoint(x: gap + CGFloat(p.c) * (cs + gap) + cs * 0.5, y: gap + CGFloat(p.r) * (cs + gap) + cs * 0.25 - cs * 0.4 * a))
                        }
                    }
                }
                .allowsHitTesting(false)
            }
            .frame(width: side, height: side)
            .offset(m.nudge)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(S.gameMergeBoardLabel)
            .accessibilityValue(m.tiles.filter { $0.kind != .gone }.sorted { ($0.r, $0.c) < ($1.r, $1.c) }.map { mergeName($0.lv) }.joined(separator: ", "))
        }
        // column 0 is on the left of the screen in every language
        .environment(\.layoutDirection, .leftToRight)
        .aspectRatio(1, contentMode: .fit)
    }

    private var buttons: some View {
        HStack(spacing: 8) {
            gameButton(S.gameMergeUndo(n: String(m.undos)), enabled: m.canUndo) { m.undo() }
            gameButton(S.gameMergeNewGame) { m.newGame() }
            SoundKey(label: S.gameMergeSound, background: boardC, size: CGSize(width: 52, height: 48))
        }
    }

    private func gameButton(_ text: String, enabled: Bool = true, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(ski.type.text(14, .bold)).foregroundStyle(paperC).lineLimit(1).minimumScaleFactor(0.8)
                .padding(.horizontal, 6).frame(maxWidth: .infinity, minHeight: 48).background(boardC)
        }
        .buttonStyle(.plain).disabled(!enabled).opacity(enabled ? 1 : 0.4)
    }

    private var ladder: some View {
        let got = max(m.got, m.maxLv)
        return CenteredFlow(spacing: 4, lineSpacing: 4) {
            ForEach(0..<Merge.levels, id: \.self) { i in
                Text(mergeName(i)).font(ski.type.text(11)).foregroundStyle(i <= got ? inkC : Color(rgb: 0x7F98B2))
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(i <= got ? paperC : boardC)
                    .overlay { if i == m.maxLv { Rectangle().stroke(accentC, lineWidth: 2) } }
            }
        }
    }

    private func toastView(_ lv: Int) -> some View {
        HStack(spacing: 12) {
            TileFace(lv: lv, labelled: false).frame(width: 56, height: 56)
            VStack(alignment: .leading, spacing: 0) {
                Text(S.gameMergeToastNew).font(ski.type.text(12)).foregroundStyle(Color(rgb: 0x4B5A6F))
                Text(mergeName(lv)).font(ski.type.display(34)).foregroundStyle(inkC)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 12).background(paperC)
        .allowsHitTesting(false)
    }

    private func endView(_ kind: String) -> some View {
        VStack(spacing: 14) {
            Text(kind == "won" ? S.gameMergeWonTitle : S.gameMergeOverTitle).font(ski.type.display(56)).foregroundStyle(paperC)
            Text(kind == "won" ? S.gameMergeWonText : S.gameMergeOverText(name: mergeName(m.maxLv), score: String(m.score)))
                .font(ski.type.text(14)).foregroundStyle(paperC).multilineTextAlignment(.center)
            Button { if kind == "won" { m.goOn() } else { withAnimation { m.newGame() } } } label: {
                Text(kind == "won" ? S.gameMergeContinue : S.gameMergeAgain).font(ski.type.display(30)).foregroundStyle(inkC)
                    .padding(.horizontal, 24).frame(minWidth: 200, minHeight: 56).background(accentC)
            }
            .buttonStyle(.plain)
            if kind == "over" && m.game.canUndo {
                Button { m.over = nil; m.undo() } label: {
                    Text(S.gameMergeUndoLast).font(ski.type.text(16, .bold)).foregroundStyle(paperC)
                        .padding(.horizontal, 16).frame(minWidth: 200, minHeight: 48).overlay { Rectangle().stroke(paperC, lineWidth: 2) }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(rgb: 0x0D1522, opacity: 0.82).ignoresSafeArea())
    }
}

/// A tile that pops in (new) or bumps (merged), once, when it appears.
private struct TileCell: View {
    let lv: Int
    let kind: TileView.Kind
    let still: Bool
    @State private var scale: CGFloat = 1

    var body: some View {
        TileFace(lv: lv, labelled: true)
            .scaleEffect(scale)
            .onAppear {
                guard !still else { return }
                if kind == .new {
                    scale = 0
                    withAnimation(.easeOut(duration: 0.2).delay(0.11)) { scale = 1 }
                } else if kind == .merged {
                    scale = 0.7
                    withAnimation(.easeOut(duration: 0.14)) { scale = 1.2 } completion: { withAnimation(.easeIn(duration: 0.12)) { scale = 1 } }
                }
            }
    }
}

/// A tile: a snowball that grows with the level, then a snowman (level 7 on) with a hat, a scarf, a giant's glow and the
/// king's crown (the site's ballSVG, in its 100 by 100 box); and the name along the bottom.
private struct TileFace: View {
    let lv: Int
    let labelled: Bool
    @Environment(\.ski) private var ski

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .bottom) {
                Canvas { c, size in TileFace.draw(&c, size, lv, labelled) }
                if labelled {
                    Text(mergeName(lv)).font(ski.type.text(g.size.width * 0.13, .bold)).foregroundStyle(Color(rgb: 0xE3ECF4))
                        .lineLimit(1).minimumScaleFactor(0.6)
                        .frame(maxWidth: .infinity).background(Color(rgb: 0x2E4E70, opacity: 0.85))
                }
            }
        }
    }

    static func draw(_ c: inout GraphicsContext, _ size: CGSize, _ lv: Int, _ labelled: Bool) {
        let w = size.width, h = size.height
        func circle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)) }
        if lv < Merge.won {
            // a ball: bigger with every step, lit from the top left, a little shadow under it
            let s = w * CGFloat(28 + lv * 8) / 100
            let cx = w / 2, cy = labelled ? h * 0.43 : h / 2
            c.fill(circle(cx, cy + 3, s / 2), with: .color(.black.opacity(0.25)))
            c.fill(circle(cx, cy, s / 2), with: .radialGradient(Gradient(colors: [.white, Color(rgb: 0xE3ECF4), Color(rgb: 0xB8CADB)]),
                                                               center: CGPoint(x: cx - s * 0.15, y: cy - s * 0.2), startRadius: 0, endRadius: s * 0.75))
            return
        }
        // the snowman, drawn in the site's 100 by 100 box (the bottom 16% left for the name)
        let k = min(w, h * (labelled ? 0.84 : 1)) / 100
        let ox = (w - 100 * k) / 2
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: ox + x * k, y: y * k) }
        let hat = lv >= 8, scarf = lv >= 9, big = lv >= 10, gold = lv >= 11
        if big { c.fill(circle(p(50, 50).x, p(50, 50).y, 48 * k), with: .color((gold ? accentC : paperC).opacity(0.25))) }
        let edge = Color(rgb: 0xB8CADB)
        let balls: [(CGFloat, CGFloat, Color)] = [(74, 22, Color(rgb: 0xEDF3F8)), (44, 16, paperC), (20, 12, Color.white)]
        for (y, r, col) in balls {
            let q = p(50, y)
            c.fill(circle(q.x, q.y, r * k), with: .color(col)); c.stroke(circle(q.x, q.y, r * k), with: .color(edge), lineWidth: 2 * k)
        }
        for (x, y) in [(CGFloat(45), CGFloat(18)), (55, 18), (50, 40), (50, 48)] { let q = p(x, y); c.fill(circle(q.x, q.y, 1.8 * k), with: .color(inkC)) }
        var nose = Path(); nose.move(to: p(50, 21)); nose.addLine(to: p(62, 23)); nose.addLine(to: p(50, 24)); nose.closeSubpath()
        c.fill(nose, with: .color(Color(rgb: 0xF07A2E)))
        if scarf {
            var s = Path(); s.move(to: p(37, 31)); s.addQuadCurve(to: p(63, 31), control: p(50, 37)); s.addLine(to: p(63, 35))
            s.addQuadCurve(to: p(37, 35), control: p(50, 41)); s.closeSubpath()
            c.fill(s, with: .color(Color(rgb: 0xD1342B)))
            c.fill(Path(CGRect(origin: p(56, 33), size: CGSize(width: 5 * k, height: 13 * k))), with: .color(Color(rgb: 0xD1342B)))
        }
        if hat && !gold {
            c.fill(Path(CGRect(origin: p(40, 2), size: CGSize(width: 20 * k, height: 9 * k))), with: .color(inkC))
            c.fill(Path(CGRect(origin: p(35, 10), size: CGSize(width: 30 * k, height: 3 * k))), with: .color(inkC))
        }
        if gold {
            var cr = Path(); cr.move(to: p(40, 6)); cr.addLine(to: p(44, 0)); cr.addLine(to: p(50, 5)); cr.addLine(to: p(56, 0)); cr.addLine(to: p(60, 6)); cr.closeSubpath()
            c.fill(cr, with: .color(accentC))
        }
    }
}
