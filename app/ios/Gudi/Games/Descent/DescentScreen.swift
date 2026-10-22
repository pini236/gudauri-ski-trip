import SwiftUI
import UIKit
import GudiDomain

/// The crew's descent on the iPhone (13.6), the site's game (site/games/descent) and Android's DescentScreen: five real
/// runs, one button, the others to pick up on the way, the avalanche behind and the ghost of the best run. Three stars a
/// run (the lift, the whole crew, the run's mission), points as on the site; the best time and its ghost are kept on the
/// phone, and the best points are the record for the group's table. No names on the characters (decision 55): you pick
/// a coat. `qa` ("play", "end") is for the screenshots: straight into a run, or to its end.
struct DescentScreen: View {
    var qa: String? = nil
    @State private var m = DescentModel()
    @Environment(\.ski) private var ski

    var body: some View {
        GeometryReader { geo in
            ZStack {
                DescentField(m: m, type: ski.type, mapTop: geo.safeAreaInsets.top + 92)
                    .ignoresSafeArea()
                    .gesture(DragGesture(minimumDistance: 0).onChanged { _ in m.down() }.onEnded { _ in m.up() },
                             including: m.phase == .play ? .all : .none)
                    .accessibilityLabel(S.gameDescentCanvasLabel)
                switch m.phase {
                case .menu:
                    ArcadeShade(top: 0x5E9BD6) { DescentMenu(m: m) }
                case .play:
                    if let g = m.game { DescentPlay(m: m, g: g) }
                case .end:
                    if let g = m.game { ArcadeShade(top: 0x5E9BD6) { DescentEnd(m: m, g: g) } }
                }
            }
        }
        .background(Color(rgb: 0x5E9BD6).ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden(m.phase == .play)
        .onAppear {
            m.load()
            if !m.qaDone {
                m.qaDone = true
                switch qa {
                case "play": m.start(); m.fastForward(4.5)
                case "end": m.start(); m.game?.toBottom()
                default: break
                }
            }
            m.resume()
        }
        .onDisappear { m.leave() }
    }
}

/// What the descent screen keeps: the choices (on the phone), the run, and what it says as it goes.
@MainActor
@Observable
final class DescentModel {
    enum Phase { case menu, play, end }
    var phase = Phase.menu
    var game: DescentGame?
    var frame = 0
    var profiles: [DescentProfile] = []
    var me = 5 { didSet { UserDefaults.standard.set(me, forKey: "descent.me") } }
    var run = 2 { didSet { UserDefaults.standard.set(run, forKey: "descent.run") } }
    var ava = true { didSet { UserDefaults.standard.set(ava, forKey: "descent.ava") } }
    var ghostOn = true { didSet { UserDefaults.standard.set(ghostOn, forKey: "descent.ghost") } }
    var menuV = 0
    // what the run says, and its numbers, as they change
    var popText: String?; var popN = 0
    var bannerNow: (big: String?, text: String, dog: Bool)?; var bannerN = 0
    var tip: String?; var tipN = 0
    var comboNow = 1; var comboN = 0
    var newBest = false
    var copied = false
    var pressed = false

    @ObservationIgnored let painter = DescentPainter()
    @ObservationIgnored private let ticker = ArcadeTicker()
    @ObservationIgnored let synth = Synth()
    @ObservationIgnored private var hiss: Synth.Hiss?
    @ObservationIgnored private var previewCache: (Int, Int, Course)?

    func load() {
        synth.start()
        guard profiles.isEmpty else { return }
        if let url = Bundle.main.url(forResource: "profiles", withExtension: "json", subdirectory: "Data"),
           let data = try? Data(contentsOf: url), let p = try? DescentProfile.parse(data) { profiles = p }
        let d = UserDefaults.standard
        me = min(max(d.object(forKey: "descent.me") as? Int ?? 5, 0), Coats.count - 1)
        run = min(max(d.object(forKey: "descent.run") as? Int ?? 2, 0), max(0, profiles.count - 1))
        ava = d.object(forKey: "descent.ava") as? Bool ?? true
        ghostOn = d.object(forKey: "descent.ghost") as? Bool ?? true
        ticker.onTick = { [weak self] dt in self?.tick(dt) }
    }

    /// The run behind the menu, still.
    var preview: Course? {
        guard run < profiles.count else { return nil }
        if let p = previewCache, p.0 == run, p.1 == me { return p.2 }
        let c = Course(profiles[run], index: run, me: me); previewCache = (run, me, c); return c
    }

    func stars(_ i: Int) -> Int { Bests.level("descent", i + 1) }
    func bestTime(_ key: String) -> Float { UserDefaults.standard.object(forKey: "descent.best-\(key)") as? Float ?? -1 }

    func start() {
        guard run < profiles.count else { return }
        let c = Course(profiles[run], index: run, me: me)
        let ghost = ghostOn ? DescentGame.Ghost.load(UserDefaults.standard.string(forKey: "descent.ghost-\(c.key)")) : nil
        popText = nil; bannerNow = nil; tip = nil; comboNow = 1; newBest = false; copied = false; pressed = false
        game = DescentGame(c, me: me, out: self, avalancheOn: ava, ghost: ghost, still: Arcade.still)
        painter.reset()
        hiss?.stop(); hiss = synth.hiss(); hiss?.freq = 700
        phase = .play
        started = Date()
        Telemetry.shared.event("game_start", ["game": "descent", "level": c.key])
        ticker.start()
    }
    /// Usage statistics (docs/GROWTH.md, as Android): level = the run, score = the points, best = a new record of them.
    @ObservationIgnored private var started: Date?
    private func gameEnd(_ g: DescentGame, _ ok: Bool, best: Bool) {
        guard let s = started else { return }
        started = nil
        Telemetry.shared.event("game_end", ["game": "descent", "level": g.c.key, "score": g.score,
            "seconds": Int(Date().timeIntervalSince(s).rounded()), "completed": ok, "best": best])
    }
    func toMenu() {
        if phase == .play, let g = game, g.state != .end { gameEnd(g, false, best: false) }
        ticker.stop(); hiss?.stop(); hiss = nil; game = nil; phase = .menu; menuV += 1
    }
    @ObservationIgnored var qaDone = false
    /// Back on the screen after it went away (a sheet, the app in the background): the clock goes on.
    func resume() {
        guard phase == .play, let g = game, g.state != .end else { return }
        if hiss == nil { hiss = synth.hiss(); hiss?.freq = 700 }
        ticker.start()
    }
    /// For the screenshots: the run as if `seconds` went by (the count, then off down the run).
    func fastForward(_ seconds: Float) {
        guard let g = game else { return }
        var t: Float = 0
        while t < seconds && g.state != .end { g.step(1 / 60); t += 1 / 60 }
        frame &+= 1
    }
    func leave() {
        if phase == .play, let g = game, g.state != .end { gameEnd(g, false, best: false) }
        ticker.stop(); hiss?.stop(); hiss = nil; synth.stop()
    }

    func down() { guard !pressed else { return }; pressed = true; game?.press(); GameHaptics.tick(0.3) }
    func up() { guard pressed else { return }; pressed = false; game?.release() }

    private func tick(_ dt: Float) {
        guard let g = game else { return }
        g.step(dt)
        frame &+= 1
        if let h = hiss {
            let run = g.state == .run || g.state == .fin
            h.gain = run ? min(0.35, g.s / 80 + (g.c.zoneAt(g.x)?.kind == Course.wind ? 0.15 : 0)) * 0.6 : 0
        }
        if g.state == .end { ticker.stop() }
    }

    func copyResult() {
        guard let g = game else { return }
        let text = g.ok
            ? S.gameDescentShareResult(run: g.c.key, time: DescentText.time(g.t), stars: String(repeating: "★", count: g.stars) + String(repeating: "☆", count: 3 - g.stars),
                                       flips: "\(g.flips)", pops: "\(g.stats.pops)", got: "\(g.got)", coins: "\(g.stats.coins)")
            : S.gameDescentShareCaught(run: g.c.key, m: "\(Int(g.x.rounded()))")
        UIPasteboard.general.string = text
        copied = true
    }
}

extension DescentModel: @preconcurrency DescentOut {
    func pop(_ w: DescentWord) { popText = DescentText.word(w); popN += 1 }
    func banner(_ big: DescentWord?, _ text: DescentWord, dog: Bool) { bannerNow = (big.map(DescentText.word), DescentText.word(text), dog); bannerN += 1 }
    func coach(_ id: String, _ w: DescentWord, force: Bool) {
        let key = "descent.coach-\(id)"
        if !force && UserDefaults.standard.bool(forKey: key) { return }
        UserDefaults.standard.set(true, forKey: key); tip = DescentText.word(w); tipN += 1
    }
    func tone(_ f: Double, _ d: Double, _ v: Float, wave: Int, end: Double) {
        synth.tone(0, f, f * end, d, v, tri: wave == DescentGame.tri, square: wave == DescentGame.square)
    }
    func buzz(_ kind: Int) {
        switch kind { case DescentGame.tick: GameHaptics.tick(0.5); case DescentGame.click: GameHaptics.click(0.6); default: GameHaptics.thud(0.9) }
    }
    func combo(_ n: Int) { comboNow = n; comboN += 1 }
    func finished(_ ok: Bool) {
        guard let g = game else { return }
        Bests.setLevel("descent", run + 1, g.stars)
        let prev = bestTime(g.c.key)
        let best = ok && (prev < 0 || g.t < prev)
        if best {
            let d = UserDefaults.standard
            d.set(g.t, forKey: "descent.best-\(g.c.key)"); d.set(me, forKey: "descent.bestWho-\(g.c.key)"); d.set(g.record().save(), forKey: "descent.ghost-\(g.c.key)")
        }
        newBest = best
        // the record for the group's table is the most points (the contract; Android's Bests.ended)
        let record = Bests.ended("descent", level: nil, score: g.score)
        gameEnd(g, ok, best: record)
        hiss?.gain = 0
        phase = .end; menuV += 1
    }
}

/// The field: the painter on a canvas, redrawn on every frame of the run.
private struct DescentField: View {
    let m: DescentModel
    let type: SkiType
    let mapTop: CGFloat
    var body: some View {
        let _ = m.frame
        let pano = Pano.image("pano-noon")
        let img = pano.map { Image(uiImage: $0) }
        let aspect = pano.map { $0.size.width / max(1, $0.size.height) } ?? 0
        let g = m.game
        let preview = g == nil ? m.preview : nil
        let still = Arcade.still
        let painter = m.painter
        Canvas { ctx, size in
            painter.mapTop = mapTop
            painter.draw(&ctx, size, game: g, preview: preview, pano: img, panoAspect: aspect, type: type, still: still)
        }
    }
}

// ---------- the menu ----------

private struct DescentMenu: View {
    let m: DescentModel
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let _ = m.menuV
        GameBack(text: S.gameDescentBackToGames, color: .white)
        ArcadeTitle(text: S.gameDescentTitle)
        Text(S.gameDescentIntro).font(ski.type.text(14)).foregroundStyle(.white).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
        ArcadeH2(text: S.gameDescentYourCoat)
        VStack(spacing: 8) {
            ForEach([0, 3], id: \.self) { row in
                HStack(spacing: 8) { ForEach(row..<row + 3, id: \.self) { i in DescentCoat(i: i, on: i == m.me) { m.me = i } } }
            }
        }
        .accessibilityElement(children: .contain).accessibilityLabel(S.gameDescentChooseCoatLabel)
        ArcadeH2(text: S.gameDescentWhichRun)
        // the runs as signs on a post, each shorter than the one above
        VStack(alignment: .leading, spacing: 8) {
            ForEach(m.profiles.indices, id: \.self) { i in
                DescentRunSign(i: i, p: m.profiles[i], on: i == m.run, stars: m.stars(i), best: m.bestTime(m.profiles[i].key)) { m.run = i }
            }
        }
        .padding(.leading, 14)
        .background(alignment: .leading) { Arcade.ink.frame(width: 7).padding(.top, -6).padding(.bottom, -10) }
        HStack(spacing: 8) {
            DescentChip(text: S.gameDescentOptAvalanche, on: m.ava) { m.ava.toggle() }
            DescentChip(text: S.gameDescentOptGhost, on: m.ghostOn) { m.ghostOn.toggle() }
        }
        HStack(spacing: 6) {
            ArcadeHow(title: S.gameDescentHowHoldTitle, text: S.gameDescentHowHoldText)
            ArcadeHow(title: S.gameDescentHowReleaseTitle, text: S.gameDescentHowReleaseText)
        }
        .fixedSize(horizontal: false, vertical: true)
        DescentLegend()
        ArcadeGo(text: S.gameDescentGo) { m.start() }
        Text(S.gameDescentProfileNote).font(ski.type.text(11.5)).foregroundStyle(.white.opacity(0.82)).fixedSize(horizontal: false, vertical: true)
    }
}

/// A coat to ski in (the site's friend buttons, without names): the skier's little picture in that coat.
private struct DescentCoat: View {
    let i: Int
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            DescentCoatIcon(color: Arcade.coat(i)).frame(width: 36, height: 36)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(on ? Color.white : Color.white.opacity(0.9))
                .overlay { if on { Rectangle().strokeBorder(Arcade.accent, lineWidth: 3) } }
                .padding(.top, 8)
                .background(alignment: .top) { Arcade.coat(i).frame(height: 8) }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Arcade.coatName(i))
        .accessibilityAddTraits(on ? [.isSelected] : [])
    }
}

/// The site's svg of a skier (viewBox -15 -28 30 30).
struct DescentCoatIcon: View {
    let color: Color
    var body: some View {
        Canvas { ctx, size in
            let k = size.width / 30
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: (x + 15) * k, y: (y + 28) * k) }
            var body = Path(); body.move(to: p(-8, -2)); body.addLine(to: p(-7, -15)); body.addQuadCurve(to: p(7, -15), control: p(0, -22)); body.addLine(to: p(8, -2)); body.closeSubpath()
            ctx.fill(body, with: .color(color))
            let head = Path(ellipseIn: CGRect(x: p(-5.5, -26.5).x, y: p(-5.5, -26.5).y, width: 11 * k, height: 11 * k))
            ctx.fill(head, with: .color(Color(rgb: 0xF4F7FB))); ctx.stroke(head, with: .color(Arcade.ink), lineWidth: 1.4 * k)
            ctx.fill(Path(CGRect(x: p(-5, -23).x, y: p(-5, -23).y, width: 10 * k, height: 3 * k)), with: .color(Arcade.ink))
            var ski = Path(); ski.move(to: p(-13, 0)); ski.addLine(to: p(13, 0))
            ctx.stroke(ski, with: .color(Arcade.ink), style: StrokeStyle(lineWidth: 3 * k, lineCap: .round))
        }
    }
}

private struct DescentRunSign: View {
    let i: Int
    let p: DescentProfile
    let on: Bool
    let stars: Int
    let best: Float
    let action: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let c = Course(p, index: i, me: 0) // the run as ridden: from its top to its lowest point
        let rtl = dir == .rightToLeft
        let stats = S.gameDescentRunCardStats(km: String(format: "%.1f", locale: Locale(identifier: "en_US"), c.len / 1000), m: "\(c.top - c.bot)")
            + (best > 0 ? S.gameDescentBestSuffix(time: DescentText.time(best)) : "")
        Button(action: action) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(alignment: .lastTextBaseline, spacing: 10) {
                    Text(p.key).font(ski.type.display(30)).foregroundStyle(.white).lineLimit(1).environment(\.layoutDirection, .leftToRight)
                    Text(String(repeating: "★", count: stars) + String(repeating: "☆", count: 3 - stars))
                        .font(.system(size: 15)).tracking(1).foregroundStyle(Arcade.accent).accessibilityHidden(true)
                }
                Text(stats).font(ski.type.text(12.5)).foregroundStyle(.white.opacity(0.95))
                if let mission = DescentText.mission(p.key) {
                    Text("★ " + mission).font(ski.type.text(12.5)).foregroundStyle(.white.opacity(0.95)).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.leading, 16).padding(.trailing, 30).padding(.vertical, 4)
            .frame(maxWidth: .infinity, minHeight: 58, alignment: .leading)
            .background(Color(rgb: descentRunColor(p.color)), in: SignShape(arrow: 18, rtl: rtl))
            .overlay(alignment: .bottom) { if on { Color.white.opacity(0.6).frame(height: 5).padding(.trailing, 18) } }
            .contentShape(SignShape(arrow: 18, rtl: rtl))
        }
        .buttonStyle(.plain)
        .containerRelativeFrame(.horizontal) { w, _ in (w - 46) * (1 - CGFloat(i) * 0.05) }
        .offset(x: on ? -10 : 0)
        .animation(Arcade.still ? nil : .spring(response: 0.3, dampingFraction: 0.55), value: on)
        .accessibilityAddTraits(on ? [.isSelected] : [])
    }
}

private struct DescentChip: View {
    let text: String
    let on: Bool
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.text(15, .bold)).foregroundStyle(on ? Arcade.ink : .white)
                .padding(.horizontal, 14).frame(minHeight: 44)
                .background(on ? Color.white : .clear)
                .overlay(Rectangle().stroke(Color.white, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? [.isSelected] : [])
    }
}

/// What is on the run, drawn with the same shapes as the game (the site's legend).
private struct DescentLegend: View {
    @Environment(\.ski) private var ski
    var body: some View {
        let items = [S.gameDescentLegendKhachapuri, S.gameDescentLegendRock, S.gameDescentLegendGully, S.gameDescentLegendSnowcat, S.gameDescentLegendSnow, S.gameDescentLegendWind]
        VStack(spacing: 6) {
            ForEach([0, 2, 4], id: \.self) { r in
                HStack(spacing: 12) {
                    ForEach(r..<r + 2, id: \.self) { k in
                        HStack(spacing: 8) {
                            Canvas { ctx, size in
                                ctx.scaleBy(x: size.width / 24, y: size.width / 24)
                                Self.icon(&ctx, k)
                            }
                            .frame(width: 24, height: 24)
                            Text(items[k]).font(ski.type.text(12.5)).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }

    static func icon(_ c: inout GraphicsContext, _ k: Int) {
        func poly(_ xy: [CGFloat]) -> Path { var p = Path(); p.move(to: CGPoint(x: xy[0], y: xy[1])); var i = 2; while i < xy.count { p.addLine(to: CGPoint(x: xy[i], y: xy[i + 1])); i += 2 }; p.closeSubpath(); return p }
        switch k {
        case 0:
            c.fill(Path(ellipseIn: CGRect(x: 2, y: 7, width: 20, height: 10)), with: .color(Color(rgb: 0xC9822E)))
            c.fill(Path(ellipseIn: CGRect(x: 5, y: 8.5, width: 14, height: 6)), with: .color(Color(rgb: 0xF7E3A1)))
            c.fill(Path(ellipseIn: CGRect(x: 10, y: 9.5, width: 4, height: 4)), with: .color(Color(rgb: 0xF4A11D)))
        case 1:
            c.fill(poly([3, 20, 6, 9, 12, 5, 18, 8, 21, 20]), with: .color(Color(rgb: 0x5B6675)))
            c.fill(poly([6, 9, 12, 4, 18, 8, 14, 9, 9, 9]), with: .color(.white))
        case 2:
            c.fill(Path(CGRect(x: 2, y: 6, width: 20, height: 12)), with: .color(Color(rgb: 0x6F88A3)))
            c.fill(Path(CGRect(x: 2, y: 14, width: 20, height: 4)), with: .color(Color(rgb: 0x3E6E9C)))
        case 3:
            c.fill(Path(CGRect(x: 4, y: 12, width: 16, height: 5)), with: .color(Color(rgb: 0x2A2F38)))
            c.fill(Path(CGRect(x: 5, y: 7, width: 12, height: 5)), with: .color(Color(rgb: 0xD1342B)))
            c.fill(Path(CGRect(x: 11, y: 3, width: 5, height: 4)), with: .color(Color(rgb: 0xD1342B)))
        case 4:
            for (x, y, r) in [(6.0, 16.0, 5.0), (13.0, 15.0, 6.0), (19.0, 16.0, 4.0)] {
                let e = Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
                c.fill(e, with: .color(.white)); c.stroke(e, with: .color(Color(rgb: 0x8FA7C0)), lineWidth: 1)
            }
        default:
            var p = Path()
            p.move(to: CGPoint(x: 2, y: 8)); p.addLine(to: CGPoint(x: 16, y: 8))
            p.move(to: CGPoint(x: 5, y: 13)); p.addLine(to: CGPoint(x: 22, y: 13))
            p.move(to: CGPoint(x: 2, y: 18)); p.addLine(to: CGPoint(x: 14, y: 18))
            c.stroke(p, with: .color(.white), lineWidth: 2.4)
        }
    }
}

// ---------- the end ----------

private struct DescentEnd: View {
    let m: DescentModel
    let g: DescentGame
    @Environment(\.ski) private var ski

    var body: some View {
        let c = g.c
        ArcadeH2(text: g.ok ? (g.got == 5 ? S.gameDescentEndAllCrew : S.gameDescentEndReachedLiftAll) : S.gameDescentEndCaught)
        VStack(alignment: .leading, spacing: 8) {
            Text(c.key + (c.lift.isEmpty ? "" : S.gameDescentEndToLiftSuffix(lift: c.lift))).font(ski.type.text(12, .bold)).foregroundStyle(Arcade.muted).textCase(.uppercase)
            Text(g.ok ? DescentText.time(g.t) : S.gameDescentEndDistance(m: "\(Int(g.x.rounded()))", total: DescentText.n(Int(c.len.rounded()))))
                .font(ski.type.display(64)).foregroundStyle(Arcade.ink).lineLimit(1).minimumScaleFactor(0.5)
            HStack(spacing: 4) { ForEach(0..<3, id: \.self) { k in Text("★").font(.system(size: 30)).foregroundStyle(g.goals[k] ? Arcade.accent : Arcade.rule) } }
                .accessibilityElement().accessibilityLabel(S.gameDescentStarsLabel(count: g.stars))
            HStack(spacing: 6) {
                ArcadeStat(label: S.gameDescentStatFlips, value: "\(g.flips)")
                ArcadeStat(label: S.gameDescentStatCrew, value: "\(g.got)/5")
                ArcadeStat(label: S.gameDescentStatKhachapuri, value: "\(g.stats.coins)/\(g.coinsTotal)")
                ArcadeStat(label: S.gameDescentStatPoints, value: "\(g.score)")
            }
            .padding(.top, 8).overlay(alignment: .top) { Arcade.rule.frame(height: 1) }
            ArcadeGoals(lines: [S.gameDescentGoalReachLift, S.gameDescentGoalBringCrewCount(got: "\(g.got)"), DescentText.mission(c.key) ?? ""], ok: g.goals)
            let best = m.bestTime(c.key)
            let bestLine = m.newBest ? S.gameDescentEndNewBest : best > 0 ? S.gameDescentEndBest(time: DescentText.time(best)) : ""
            if !bestLine.isEmpty { Text(bestLine).font(ski.type.text(13)).foregroundStyle(Arcade.muted) }
        }
        .padding(14)
        .background(Color.white)
        .shadow(color: .black.opacity(0.3), radius: 10, y: 4)
        .rotationEffect(.degrees(-2))
        .padding(.vertical, 6)
        ArcadeGo(text: S.gameDescentAgain) { m.start() }
        HStack(spacing: 8) {
            ArcadeOutline(text: S.gameDescentToMenu) { m.toMenu() }
            ArcadeOutline(text: S.gameDescentCopyResult) { m.copyResult() }
        }
        if m.copied { Text(S.gameDescentCopied).font(ski.type.text(11.5)).foregroundStyle(.white.opacity(0.82)) }
    }
}

// ---------- on the run ----------

private struct DescentPlay: View {
    let m: DescentModel
    let g: DescentGame
    @Environment(\.ski) private var ski

    var body: some View {
        let _ = m.frame
        ZStack {
            DescentHud(m: m, g: g)
            DescentBanner(banner: m.bannerNow, n: m.bannerN)
            ArcadePop(text: m.popText, n: m.popN, at: 0.34, maxSize: 56)
            DescentPad(m: m, g: g)
            if g.state == .count { DescentCount(g: g) }
        }
    }
}

private struct DescentHud: View {
    let m: DescentModel
    let g: DescentGame
    @Environment(\.ski) private var ski
    @State private var bump: CGFloat = 1

    var body: some View {
        let gi = g.ghostAt(g.t)
        let gap: Int? = gi >= 0 ? Int((g.x - (g.ghost?.r[gi + 1] ?? g.x)).rounded()) : nil
        ZStack(alignment: .topLeading) {
            // the run's sign, and the time, the speed, the height and the ghost
            HStack(alignment: .top) {
                Text(g.c.key).font(ski.type.display(26)).foregroundStyle(.white).environment(\.layoutDirection, .leftToRight)
                    .padding(.leading, 14).padding(.trailing, 26).padding(.vertical, 4)
                    .background(Color(rgb: descentRunColor(g.c.color)), in: SignShape(arrow: 16, rtl: false))
                    .environment(\.layoutDirection, .leftToRight)
                Spacer()
                VStack(alignment: .leading, spacing: 1) {
                    Text(DescentText.time(g.t)).font(ski.type.display(36)).foregroundStyle(.white).monospacedDigit()
                    Text("\(Int((g.s * 3.6).rounded())) \(S.gameDescentHudSpeedUnit) · \(S.gameDescentMeters(m: DescentText.n(Int(g.c.realH(g.x).rounded()))))")
                        .font(ski.type.text(11, .bold)).foregroundStyle(Arcade.accent)
                    if let gap {
                        Text(gap >= 0 ? S.gameDescentHudGapAhead(m: "\(abs(gap))") : S.gameDescentHudGapBehind(m: "\(abs(gap))"))
                            .font(ski.type.text(11, .bold)).foregroundStyle(Arcade.accent)
                    }
                }
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Arcade.ink.opacity(0.84))
            }
            .padding(.horizontal, 12).padding(.top, 10)
            // the others: a square each, on once picked up, faint when lost; and the khachapuri
            HStack(spacing: 4) {
                ForEach(g.c.spots.indices, id: \.self) { k in
                    let sp = g.c.spots[k]
                    let got = g.crew.contains { $0.coat == sp.coat }
                    Arcade.coat(sp.coat).frame(width: 14, height: 14).overlay(Rectangle().strokeBorder(.white, lineWidth: 2))
                        .opacity(sp.lost ? 0.12 : got ? 1 : 0.35)
                }
                Text("\(g.stats.coins) 🥟").font(ski.type.text(13, .bold)).foregroundStyle(.white).shadow(color: Arcade.ink.opacity(0.6), radius: 1, y: 1).padding(.leading, 6)
            }
            .padding(.top, 62).padding(.leading, 12)
            .accessibilityHidden(true)
            // the combo beside the sound, and the way out, on the left in every language (the site's)
            ZStack(alignment: .topLeading) {
                SoundKey(label: S.gameDescentSound).padding(.top, 120).padding(.leading, 12)
                ArcadeExitKey(label: S.gameDescentToMenu) { m.toMenu() }.padding(.top, 172).padding(.leading, 12)
                Text(S.gameDescentCombo(n: "\(m.comboNow)")).font(ski.type.display(24)).foregroundStyle(Arcade.ink)
                    .padding(.horizontal, 10).padding(.vertical, 3).background(Arcade.accent)
                    .scaleEffect(bump).opacity(m.comboNow > 1 ? 1 : 0.45)
                    .padding(.top, 128).padding(.leading, 64)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .environment(\.layoutDirection, .leftToRight)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .onChange(of: m.comboN) {
            guard !Arcade.still else { return }
            bump = 1.25
            withAnimation(.easeOut(duration: 0.2)) { bump = 1 }
        }
    }
}

/// A warning sliding in from the right (the site's .banner), for 2.6 seconds.
private struct DescentBanner: View {
    let banner: (big: String?, text: String, dog: Bool)?
    let n: Int
    @Environment(\.ski) private var ski
    @State private var on = false

    var body: some View {
        GeometryReader { geo in
            if let b = banner {
                HStack(spacing: 8) {
                    if let big: String = b.dog ? Optional("🐕") : b.big { Text(big).font(ski.type.display(24)).foregroundStyle(Arcade.ink).lineLimit(1) }
                    Text(b.text).font(ski.type.text(15, .bold)).foregroundStyle(Arcade.ink).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.leading, 30).padding(.trailing, 14).padding(.vertical, 6)
                .background(Arcade.warn, in: SignShape(arrow: 18, rtl: true))
                .frame(maxWidth: geo.size.width - 120, alignment: .trailing)
                .offset(x: on ? 0 : geo.size.width)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.top, 122).padding(.trailing, 12)
            }
        }
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
        .task(id: n) {
            guard n > 0 else { return }
            withAnimation(Arcade.still ? nil : .spring(response: 0.35, dampingFraction: 0.6)) { on = true }
            try? await Task.sleep(for: .seconds(2.6))
            withAnimation(Arcade.still ? nil : .easeIn(duration: 0.3)) { on = false }
        }
    }
}

/// The one button (the site's .pad): hold to crouch and load (it fills), let go to jump; in the air, hold to flip.
private struct DescentPad: View {
    let m: DescentModel
    let g: DescentGame
    @Environment(\.ski) private var ski
    @State private var coaching = false
    @State private var pulse = false

    private static func words(_ pad: Int) -> (String, String) {
        switch pad {
        case 1: (S.gameDescentCoachFlip, S.gameDescentCoachFlipHint)
        case 2: (S.gameDescentCoachRelease, S.gameDescentCoachReleaseHint)
        default: (S.gameDescentJump, S.gameDescentCoachJumpHint)
        }
    }

    var body: some View {
        let pad = g.air ? 1 : g.holding && g.loading ? 2 : 0
        let load = pad == 2 ? CGFloat(min(1, g.loadT / 0.32)) : 0
        let words = Self.words(pad)
        let big = words.0, small = words.1
        let down = m.pressed
        let fg = down ? Arcade.ink : Color.white
        VStack(spacing: 10) {
            Spacer()
            if coaching, let tip = m.tip {
                Text(tip).font(ski.type.text(14, .bold)).foregroundStyle(Arcade.ink)
                    .padding(.horizontal, 10).padding(.vertical, 6).background(Color.white).shadow(radius: 4)
            }
            HStack(spacing: 10) {
                Image(systemName: "arrow.up").font(.system(size: 22, weight: .bold)).foregroundStyle(fg)
                VStack(alignment: .leading, spacing: 0) {
                    Text(big).font(ski.type.display(28)).foregroundStyle(fg).lineLimit(1)
                    Text(small).font(ski.type.text(12)).foregroundStyle(fg.opacity(0.85)).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 84)
            .background(alignment: .leading) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        (down ? Arcade.accent : Arcade.ink.opacity(0.55))
                        if load > 0 { Color.white.opacity(0.35).frame(width: geo.size.width * load) }
                    }
                }
                .environment(\.layoutDirection, .leftToRight)
            }
            .overlay(Rectangle().strokeBorder(down || (coaching && pulse) ? Arcade.accent : Color.white.opacity(0.75), lineWidth: 2))
            .background { if coaching { Arcade.accent.opacity(pulse ? 0.45 : 0.1).padding(-6) } }
            .scaleEffect(down ? 0.97 : 1)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { _ in m.down() }.onEnded { _ in m.up() })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(S.gameDescentJump)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { m.down(); m.up() }
        }
        .padding(12)
        .task(id: m.tipN) {
            guard m.tipN > 0 else { return }
            coaching = true
            if !Arcade.still { withAnimation(.easeInOut(duration: 0.5).repeatForever()) { pulse = true } } else { pulse = true }
            try? await Task.sleep(for: .seconds(3))
            coaching = false; pulse = false
        }
    }
}

/// 3, 2, 1, go: and the run's three stars.
private struct DescentCount: View {
    let g: DescentGame
    @Environment(\.ski) private var ski
    var body: some View {
        let n = Int(g.countT.rounded(.up))
        VStack(spacing: 10) {
            Text(n > 0 ? "\(n)" : S.gameDescentCountGo).font(ski.type.display(140)).foregroundStyle(.white)
                .shadow(color: Arcade.ink, radius: 0, x: 0, y: 10).lineLimit(1).minimumScaleFactor(0.4)
            VStack(alignment: .leading, spacing: 4) {
                Text(S.gameDescentCountThreeStars(run: g.c.key)).font(ski.type.text(12, .bold)).foregroundStyle(Arcade.accent)
                ForEach([S.gameDescentGoalReachLift, S.gameDescentGoalBringCrew, DescentText.mission(g.c.key) ?? ""], id: \.self) { s in
                    Text("★ " + s).font(ski.type.text(14)).foregroundStyle(.white)
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .frame(maxWidth: 360, alignment: .leading)
            .background(Arcade.ink.opacity(0.86))
        }
        .padding(.horizontal, 16)
        .allowsHitTesting(false)
    }
}
